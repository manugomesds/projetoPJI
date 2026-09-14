- =============================================================
-- 4. fn_verificar_perfil_completo — versão limpa e com checagem
--    de CPF/CNPJ por subtipo (RF01)
-- =============================================================
 
create or replace function fn_verificar_perfil_completo(p_usuario_id bigint)
returns boolean as $$
declare
    v_tipo tipo_usuario_enum;
    v_cpf varchar;
    v_cnpj varchar;
    v_completo boolean := false;
 
    v_tipo_perfil_artistico tipo_perfil_artistico_enum;
    v_bio_loc_ok boolean;
    v_portfolio_ok boolean;
    v_cnpj_ok boolean;
    v_area_principal_ok boolean;
    v_funcao_ok boolean;
    v_especializacao_ok boolean;
 
    v_tipo_contratante tipo_contratante_enum;
    v_contratante_ok boolean;
begin
    select tipo_usuario, cpf, cnpj into v_tipo, v_cpf, v_cnpj
    from usuarios where id = p_usuario_id;
 
    if v_tipo = 'ARTISTA' then
        -- RF01: CPF é sempre obrigatório para o artista (solo ou
        -- responsável da conta), mesmo para Estúdio/Produtora
        if v_cpf is null then
            update usuarios set perfil_completo = false where id = p_usuario_id;
            return false;
        end if;
 
        select tipo_perfil_artistico,
               (biografia is not null and trim(biografia) <> '' and cidade is not null and estado is not null),
               (url_portfolio is not null and trim(url_portfolio) <> '')
        into v_tipo_perfil_artistico, v_bio_loc_ok, v_portfolio_ok
        from perfis_artistas
        where usuario_id = p_usuario_id;
 
        -- RF01: CNPJ adicionalmente obrigatório só para Estúdio/Produtora
        v_cnpj_ok := (v_tipo_perfil_artistico not in ('ESTUDIO', 'PRODUTORA_EMPRESA')) or (v_cnpj is not null);
 
        select exists (
            select 1 from perfil_artista_area
            where perfil_artista_id = p_usuario_id
              and principal = true
              and nivel_experiencia is not null
        ) into v_area_principal_ok;
 
        select exists (
            select 1
            from perfil_artista_funcao paf
            join perfil_artista_area paa
              on paa.perfil_artista_id = paf.perfil_artista_id and paa.area_id = paf.area_id
            where paf.perfil_artista_id = p_usuario_id and paa.principal = true
        ) into v_funcao_ok;
 
        select exists (
            select 1
            from perfil_artista_especializacao pae
            join perfil_artista_area paa
              on paa.perfil_artista_id = pae.perfil_artista_id and paa.area_id = pae.area_id
            where pae.perfil_artista_id = p_usuario_id and paa.principal = true
        ) into v_especializacao_ok;
 
        v_completo := coalesce(v_bio_loc_ok, false)
                  and coalesce(v_portfolio_ok, false)
                  and coalesce(v_cnpj_ok, false)
                  and coalesce(v_area_principal_ok, false)
                  and coalesce(v_funcao_ok, false)
                  and coalesce(v_especializacao_ok, false);
 
    elsif v_tipo = 'CONTRATANTE' then
        select
            tipo_contratante,
            (cidade is not null and estado is not null)
            and (tipo_contratante = 'PESSOA_FISICA' or (nome_empresa is not null and trim(nome_empresa) <> ''))
            and (
                (tipo_contratante = 'PESSOA_FISICA' and v_cpf is not null)
                or (tipo_contratante <> 'PESSOA_FISICA' and v_cnpj is not null)
            )
        into v_tipo_contratante, v_contratante_ok
        from perfis_contratantes
        where usuario_id = p_usuario_id;
 
        v_completo := coalesce(v_contratante_ok, false);
    end if;
 
    update usuarios set perfil_completo = v_completo where id = p_usuario_id;
    return v_completo;
end;
$$ language plpgsql;
 
