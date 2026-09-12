create or replace procedure sp_redefinir_senha(
    p_token_hash varchar,
    p_nova_senha_hash varchar
)
language plpgsql as $$
declare
    v_usuario_id bigint;
    v_expiracao timestamp;
begin
    -- 1. Localiza o usuário pelo HASH SHA-256 do token (RF09)
    select id, token_expiracao
    into v_usuario_id, v_expiracao
    from usuarios
    where token_recuperacao = p_token_hash;

    if v_usuario_id is null then
        raise exception 'Token de recuperação inválido ou não encontrado.'
            using errcode = '22000';
    end if;

    -- 2. Valida se o token expirou
    if v_expiracao < current_timestamp then
        raise exception 'O token de recuperação expirou. Solicite um novo link de redefinição.'
            using errcode = '22000';
    end if;

    -- 3. Atualiza a credencial e limpa os campos de token consumido
    update usuarios
    set senha_hash = p_nova_senha_hash,
        token_recuperacao = null,
        token_expiracao = null
    where id = v_usuario_id;

    -- 4. Invalida todas as sessões persistentes ativas (Refresh Tokens) do usuário
    delete from refresh_tokens
    where usuario_id = v_usuario_id;
end;
$$;