create or replace function fn_verificar_perfil_completo(p_usuario_id bigint)
returns boolean as $$
declare
    v_tipo tipo_usuario_enum;
    v_completo boolean := false;create or replace function fn_verificar_perfil_completo(p_usuario_id bigint)
returns boolean as $$
declare
    v_tipo tipo_usuario_enum;
    v_cpf varchar(14);
    v_cnpj varchar(14);
    v_completo boolean := false;
    
    -- auxiliares artista
    v_bio_loc_ok boolean;
    v_portfolio_ok boolean;
    v_area_principal_ok boolean;
    v_funcao_ok boolean;
    v_especializacao_ok boolean;
    
    -- auxiliares contratante
    v_contratante_ok boolean;
begin
    -- 1. obtém dados base de identificação
    select tipo_usuario, cpf, cnpj 
    into v_tipo, v_cpf, v_cnpj
    from usuarios
    where id = p_usuario_id;

    -- documento básico (cpf ou cnpj) deve existir
    if v_cpf is null and v_cnpj is null then
        update usuarios set perfil_completo = false where id = p_usuario_id;
        return false;
    end if;

    if v_tipo = 'ARTISTA' then
        -- checa biografia, localização e url_portfolio
        select 
            (biografia is not null and trim(biografia) <> '' 
             and (cidade is not null or localizacao is not null)),
            (url_portfolio is not null and trim(url_portfolio) <> '')
        into v_bio_loc_ok, v_portfolio_ok
        from perfis_artistas
        where usuario_id = p_usuario_id;

        -- checa área principal com nível de experiência preenchido
        select exists (
            select 1 
            from perfil_artista_area 
            where perfil_artista_id = p_usuario_id 
              and principal = true 
              and nivel_experiencia is not null
        ) into v_area_principal_ok;

        -- checa se possui ao menos uma função
        select exists (
            select 1 
            from perfil_artista_funcao 
            where perfil_artista_id = p_usuario_id
        ) into v_funcao_ok;

        -- checa se possui ao menos uma especialização
        select exists (
            select 1 
            from perfil_artista_especializacao 
            where perfil_artista_id = p_usuario_id
        ) into v_especializacao_ok;

        v_completo := coalesce(v_bio_loc_ok, false) 
                  and coalesce(v_portfolio_ok, false) 
                  and v_area_principal_ok 
                  and v_funcao_ok 
                  and v_especializacao_ok;

    elsif v_tipo = 'CONTRATANTE' then
        -- se for pj (cnpj not null), exige nome_empresa. Se for pf (cpf), nome_empresa é opcional
        select 
            (cidade is not null or localizacao is not null)
            and (v_cnpj is null or (nome_empresa is not null and trim(nome_empresa) <> ''))
        into v_contratante_ok
        from perfis_contratantes
        where usuario_id = p_usuario_id;

        v_completo := coalesce(v_contratante_ok, false);
    end if;

    -- atualiza e retorna o status final
    update usuarios
    set perfil_completo = v_completo
    where id = p_usuario_id;

    return v_completo;
end;
$$ language plpgsql;
    v_tem_bio boolean;
    v_tem_empresa boolean;
begin
    select tipo_usuario into v_tipo
    from usuarios
    where id = p_usuario_id;

    if v_tipo = 'ARTISTA' then
        select (biografia is not null and localizacao is not null) into v_tem_bio
        from perfis_artistas
        where usuario_id = p_usuario_id;
        
        v_completo := coalesce(v_tem_bio, false);
        
    elsif v_tipo = 'CONTRATANTE' then
        select (nome_empresa is not null and localizacao is not null) into v_tem_empresa
        from perfis_contratantes
        where usuario_id = p_usuario_id;
        
        v_completo := coalesce(v_tem_empresa, false);
    end if;

    update usuarios
    set perfil_completo = v_completo
    where id = p_usuario_id;

    return v_completo;
end;
$$ language plpgsql;