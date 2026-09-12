create or replace procedure sp_cancelar_vaga(
    p_vaga_id bigint,
    p_cancelado_por_id bigint,
    p_motivo text
)
language plpgsql as $$
declare
    v_status_atual status_vaga_enum;
    v_contratante_id bigint;
begin
    -- 1. Obtém o status e o proprietário da vaga
    select status, contratante_id 
    into v_status_atual, v_contratante_id
    from vagas
    where id = p_vaga_id;

    if v_status_atual is null then
        raise exception 'Vaga (ID: %) não encontrada.', p_vaga_id;
    end if;

    -- 2. Validação de permissão do contratante
    if v_contratante_id <> p_cancelado_por_id then
        raise exception 'Usuário (ID: %) não tem permissão para cancelar esta vaga.', p_cancelado_por_id;
    end if;

    -- 3. Guarda de Estado: permite cancelar apenas vagas ABERTA, PAUSADA ou RASCUNHO
    if v_status_atual not in ('ABERTA', 'PAUSADA', 'RASCUNHO') then
        raise exception 'Não é possível cancelar uma vaga no status atual: %.', v_status_atual;
    end if;

    -- 4. Atualiza o status da vaga com cast explícito
    update vagas
    set status = 'CANCELADA'::status_vaga_enum
    where id = p_vaga_id;

    -- 5. Grava o log de cancelamento
    insert into log_vagas_canceladas (vaga_id, cancelado_por_id, motivo)
    values (p_vaga_id, p_cancelado_por_id, p_motivo);

    -- 6. Atualiza todas as candidaturas ativas (PENDENTE e EM_ANALISE)
    update candidaturas
    set status = 'CANCELADA_POR_VAGA'::status_candidatura_enum
    where vaga_id = p_vaga_id 
      and status in ('PENDENTE', 'EM_ANALISE');
end;
$$;