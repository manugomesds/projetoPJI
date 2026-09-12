create or replace procedure sp_salvar_item(
    p_usuario_id bigint,
    p_tipo_alvo tipo_alvo_salvo_enum,
    p_alvo_id bigint
)
language plpgsql as $$
begin
    -- 1. Validação de existência do usuário
    if not exists (select 1 from usuarios where id = p_usuario_id) then
        raise exception 'Usuário (ID: %) não encontrado.', p_usuario_id;
    end if;

    -- 2. Inserção idempotente com registro de data de interação
    insert into itens_salvos (usuario_id, tipo_alvo, alvo_id, data_salvamento)
    values (p_usuario_id, p_tipo_alvo, p_alvo_id, current_timestamp)
    on conflict (usuario_id, tipo_alvo, alvo_id) do nothing;
end;
$$;