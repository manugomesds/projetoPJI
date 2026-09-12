create or replace procedure sp_enviar_candidatura(
    inout p_candidatura_id bigint default null,
    p_vaga_id bigint default null,
    p_artista_id bigint default null,
    p_mensagem text default null,
    p_link_portfolio varchar default null
)
language plpgsql as $$
declare
    v_status_vaga status_vaga_enum;
    v_perfil_completo boolean;
    v_status_conta status_conta_enum;
    v_tipo_usuario text;
begin
    -- 1. Validação de existência e status da Vaga (RF06)
    select status into v_status_vaga
    from vagas
    where id = p_vaga_id;

    if v_status_vaga is null then
        raise exception 'Vaga (ID: %) não encontrada.', p_vaga_id
            using errcode = 'P0002';
    end if;

    if v_status_vaga <> 'ABERTA'::status_vaga_enum then
        raise exception 'Candidatura recusada: a vaga não aceita candidaturas (Status: %).', v_status_vaga
            using errcode = '22000';
    end if;

    -- 2. Validação do Usuário e Perfil Completo (RF06)
    select upper(u.tipo_usuario::text), u.perfil_completo, u.status_conta
    into v_tipo_usuario, v_perfil_completo, v_status_conta
    from usuarios u
    where u.id = p_artista_id;

    if v_tipo_usuario is null or v_tipo_usuario <> 'ARTISTA' then
        raise exception 'Apenas usuários cadastrados como ARTISTA podem se candidatar a vagas.'
            using errcode = '22000';
    end if;

    if v_status_conta <> 'ATIVO'::status_conta_enum then
        raise exception 'Candidatura recusada: a conta do artista não está ativa.'
            using errcode = '22000';
    end if;

    if not coalesce(v_perfil_completo, false) then
        raise exception 'Candidatura recusada: o perfil do artista deve estar completo para se candidatar (RF06).'
            using errcode = '22000';
    end if;

    -- 3. Impede candidatura duplicada
    if exists (
        select 1 from candidaturas
        where vaga_id = p_vaga_id and artista_id = p_artista_id
    ) then
        raise exception 'O artista já se candidatou a esta vaga anteriormente.'
            using errcode = '23505';
    end if;

    -- 4. Registro da candidatura
    insert into candidaturas (
        vaga_id,
        artista_id,
        mensagem_apresentacao,
        link_portfolio_candidatura,
        status,
        data_candidatura
    ) values (
        p_vaga_id,
        p_artista_id,
        p_mensagem,
        p_link_portfolio,
        'PENDENTE'::status_candidatura_enum,
        current_timestamp
    )
    returning id into p_candidatura_id;
end;
$$;