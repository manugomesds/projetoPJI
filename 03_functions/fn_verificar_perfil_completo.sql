create or replace function fn_verificar_perfil_completo(p_usuario_id bigint)
returns boolean as $$
declare
    v_tipo tipo_usuario_enum;
    v_completo boolean := false;
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