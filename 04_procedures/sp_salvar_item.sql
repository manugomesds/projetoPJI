create or replace procedure sp_salvar_item(
    p_usuario_id bigint,
    p_tipo_alvo tipo_alvo_salvo_enum,
    p_alvo_id bigint
)
language plpgsql as $$
begin
    insert into itens_salvos (usuario_id, tipo_alvo, alvo_id)
    values (p_usuario_id, p_tipo_alvo, p_alvo_id)
    on conflict (usuario_id, tipo_alvo, alvo_id) do nothing;
end;
$$;