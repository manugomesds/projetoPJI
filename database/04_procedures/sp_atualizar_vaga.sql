create or replace procedure sp_atualizar_vaga(
    p_vaga_id bigint,
    p_contratante_id bigint,
    p_titulo varchar default null,
    p_descricao text default null,
    p_requisitos text default null,
    p_valor_minimo numeric default null,
    p_valor_maximo numeric default null
)
language plpgsql as $$
declare
    v_status status_vaga_enum;
begin
    -- 1. Verifica existência, propriedade e status atual
    select status into v_status
    from vagas
    where id = p_vaga_id and contratante_id = p_contratante_id;

    if v_status is null then
        raise exception 'Vaga não encontrada ou contratante sem permissão.';
    end if;

    -- 2. Impede alteração cadastral em estados finais do ciclo de vida
    if v_status in ('ENCERRADA', 'CANCELADA') then
        raise exception 'Não é permitido alterar dados de vagas encerradas, canceladas ou arquivadas.';
    end if;

    -- 3. Atualização exclusiva dos dados informativos da vaga
    update vagas
    set titulo = coalesce(p_titulo, titulo),
        descricao = coalesce(p_descricao, descricao),
        requisitos = coalesce(p_requisitos, requisitos),
        valor_minimo = coalesce(p_valor_minimo, valor_minimo),
        valor_maximo = coalesce(p_valor_maximo, valor_maximo)
    where id = p_vaga_id and contratante_id = p_contratante_id;
end;
$$;