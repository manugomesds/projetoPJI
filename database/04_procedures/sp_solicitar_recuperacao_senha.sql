create or replace procedure sp_solicitar_recuperacao_senha(
    p_email varchar,
    p_token varchar,
    p_expiracao timestamp,
    inout p_status_ramificacao varchar default null
)
language plpgsql as $$
declare
    v_usuario_id bigint;
    v_senha_hash varchar;
    v_status_conta status_conta_enum;
begin
    -- 1. Consulta o usuário pelo e-mail
    select id, senha_hash, status_conta
    into v_usuario_id, v_senha_hash, v_status_conta
    from usuarios
    where lower(email) = lower(p_email);

    -- Ramificação 1: E-mail não encontrado
    if v_usuario_id is null then
        p_status_ramificacao := 'USUARIO_INEXISTENTE';
        return;
    end if;

    -- Ramificação 2: Conta exclusiva via Google (sem hash de senha local)
    if v_senha_hash is null or trim(v_senha_hash) = '' then
        p_status_ramificacao := 'CONTA_GOOGLE_ONLY';
        return;
    end if;

    -- Proteção Adicional: Impede recuperação para contas inativas ou bloqueadas
    if v_status_conta in ('BLOQUEADO'::status_conta_enum, 'DESATIVADO'::status_conta_enum) then
        p_status_ramificacao := 'CONTA_INATIVA';
        return;
    end if;

    -- Ramificação 3: Conta convencional com senha local
    update usuarios
    set token_recuperacao = p_token,
        token_expiracao = p_expiracao
    where id = v_usuario_id;

    p_status_ramificacao := 'SUCESSO_TOKEN_GERADO';
end;
$$;