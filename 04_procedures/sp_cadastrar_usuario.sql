create or replace procedure sp_cadastrar_usuario(
    p_nome varchar,
    p_data_nascimento date,
    p_telefone varchar,
    p_email varchar,
    p_senha varchar,
    p_tipo_usuario tipo_usuario_enum,
    p_foto_perfil varchar default null,
    p_nome_resp varchar default null,
    p_tel_resp varchar default null,
    p_email_resp varchar default null
)
language plpgsql as $$
declare
    v_usuario_id bigint;
    v_idade int;
begin
    insert into usuarios (
        nome, data_nascimento, telefone, email, senha, tipo_usuario, foto_perfil
    ) values (
        p_nome, p_data_nascimento, p_telefone, p_email, p_senha, p_tipo_usuario, p_foto_perfil
    ) returning id into v_usuario_id;

    if p_tipo_usuario = 'artista' then
        insert into perfis_artistas (usuario_id) values (v_usuario_id);
    else
        insert into perfis_contratantes (usuario_id) values (v_usuario_id);
    end if;

    -- Validação de menoridade para cadastro de responsável legal normalizado (RF27)
    v_idade := extract(year from age(current_date, p_data_nascimento));
    if v_idade < 18 and p_nome_resp is not null then
        insert into responsaveis_legais (
            usuario_id, nome_responsavel, telefone_responsavel, email_responsavel
        ) values (
            v_usuario_id, p_nome_resp, p_tel_resp, p_email_resp
        );
    end if;
end;
$$;