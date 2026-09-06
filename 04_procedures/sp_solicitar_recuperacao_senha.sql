create or replace procedure sp_solicitar_recuperacao_senha(
    p_email varchar,
    p_token varchar,
    p_expiracao timestamp
)
language plpgsql as $$
begin
    update usuarios
    set token_recuperacao = p_token,
        token_expiracao = p_expiracao
    where email = p_email;
end;
$$;