create or replace procedure sp_atualizar_vaga(
    p_vaga_id bigint,
    p_contratante_id bigint,
    p_titulo varchar,
    p_descricao text,
    p_requisitos text,
    p_remunera_valor numeric,
    p_status status_vaga_enum
)
language plpgsql as $$
begin
    update vagas
    set titulo = coalesce(p_titulo, titulo),
        descricao = coalesce(p_descricao, descricao),
        requisitos = coalesce(p_requisitos, requisitos),
        remunera_valor = coalesce(p_remunera_valor, remunera_valor),
        status = coalesce(p_status, status)
    where id = p_vaga_id and contratante_id = p_contratante_id;
end;
$$;