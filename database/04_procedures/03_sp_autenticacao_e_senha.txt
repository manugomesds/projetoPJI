CREATE OR REPLACE PROCEDURE sp_solicitar_recuperacao_senha(
    p_email VARCHAR,
    INOUT p_eh_conta_google BOOLEAN DEFAULT FALSE
)
LANGUAGE plpgsql AS $$
DECLARE
    v_usuario_id INT;
    v_senha_hash VARCHAR;
    v_token VARCHAR;
BEGIN
    SELECT id, senha_hash INTO v_usuario_id, v_senha_hash 
    FROM usuarios 
    WHERE email = p_email;

    IF v_usuario_id IS NULL THEN
        RAISE EXCEPTION 'Usuário não encontrado.' USING ERRCODE = '28000';
    END IF;

    IF v_senha_hash IS NULL THEN
        p_eh_conta_google := TRUE;
        RETURN;
    END IF;

    v_token := gen_random_uuid()::text;

    UPDATE usuarios 
    SET reset_token = v_token,
        reset_token_expiracao = NOW() + INTERVAL '1 hour'
    WHERE id = v_usuario_id;
END;
$$;

CREATE OR REPLACE PROCEDURE sp_redefinir_senha(
    p_token VARCHAR,
    p_nova_senha_hash VARCHAR
)
LANGUAGE plpgsql AS $$
DECLARE
    v_usuario_id INT;
BEGIN
    SELECT id INTO v_usuario_id 
    FROM usuarios 
    WHERE reset_token = p_token 
      AND reset_token_expiracao > NOW();

    IF v_usuario_id IS NULL THEN
        RAISE EXCEPTION 'Token inválido ou expirado.' USING ERRCODE = '22000';
    END IF;

    UPDATE usuarios 
    SET senha_hash = p_nova_senha_hash,
        reset_token = NULL,
        reset_token_expiracao = NULL,
        atualizado_em = NOW()
    WHERE id = v_usuario_id;

    UPDATE refresh_tokens 
    SET ativo = FALSE 
    WHERE usuario_id = v_usuario_id;
END;
$$;

CREATE OR REPLACE PROCEDURE sp_revogar_sessoes_usuario(
    p_usuario_id INT,
    p_token_hash VARCHAR
)
LANGUAGE plpgsql AS $$
BEGIN
    UPDATE refresh_tokens 
    SET ativo = FALSE 
    WHERE usuario_id = p_usuario_id AND token_hash = p_token_hash;
END;
$$;