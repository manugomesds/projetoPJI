create or replace procedure sp_atualizar_perfil(
    p_usuario_id bigint,
    p_nome varchar,
    p_telefone varchar,
    p_foto_perfil varchar,
    p_bio text,
    p_localizacao varchar,
    p_extra varchar -- url_portfolio para artistas ou nome_empresa para contratantes
)
language plpgsql as $$
declare
    v_tipo tipo_usuario_enum;
begin
    update usuarios 
    set nome = coalesce(p_nome, nome),
        telefone = coalesce(p_telefone, telefone),
        foto_perfil = coalesce(p_foto_perfil, foto_perfil)
    where id = p_usuario_id;

    select tipo_usuario into v_tipo from usuarios where id = p_usuario_id;

    if v_tipo = 'artista' then
        update perfis_artistas
        set biografia = coalesce(p_bio, biografia),
            localizacao = coalesce(p_localizacao, localizacao),
            url_portfolio = coalesce(p_extra, url_portfolio),
            ultima_atualizacao = current_timestamp
        where usuario_id = p_usuario_id;
    elsif v_tipo = 'contratante' then
        update perfis_contratantes
        set biografia = coalesce(p_bio, biografia),
            localizacao = coalesce(p_localizacao, localizacao),
            nome_empresa = coalesce(p_extra, nome_empresa)
        where usuario_id = p_usuario_id;
    end if;

    perform fn_verificar_perfil_completo(p_usuario_id);
end;
$$;