create or replace procedure sp_remover_item(
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

    -- 2. Remoção do item salvo
    delete from itens_salvos
    where usuario_id = p_usuario_id
      and tipo_alvo = p_tipo_alvo
      and alvo_id = p_alvo_id;

    if not found then
        raise notice 'Item (Tipo: %, ID: %) não estava na lista de salvos do usuário %.', 
            p_tipo_alvo, p_alvo_id, p_usuario_id;
    end if;
end;
$$;