create or replace procedure sp_cancelar_vaga(
    p_vaga_id bigint,
    p_cancelado_por_id bigint,
    p_motivo text
)
language plpgsql as $$
begin
    update vagas
    set status = 'cancelada'
    where id = p_vaga_id;

    insert into log_vagas_canceladas (vaga_id, cancelado_por_id, motivo)
    values (p_vaga_id, p_cancelado_por_id, p_motivo);

    update candidaturas
    set status = 'cancelada_por_vaga'
    where vaga_id = p_vaga_id and status = 'pendente';
end;
$$;