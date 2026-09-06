create or replace procedure sp_excluir_conta_lgpd(
    p_usuario_id bigint,
    p_motivo text,
    p_comprovante_hash char(64)
)
language plpgsql as $$
begin
    -- Armazena o log de exclusão anônimo obrigatório por conformidade LGPD
    insert into log_exclusoes_lgpd (usuario_id_antigo, motivo_opcional, comprovante_hash)
    values (p_usuario_id, p_motivo, p_comprovante_hash);

    -- Exclui o usuário (o CASCADE apaga perfis/tokens, e o SET NULL preserva as mensagens de chat)
    delete from usuarios where id = p_usuario_id;
end;
$$;