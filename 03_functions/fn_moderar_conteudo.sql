create or replace function fn_moderar_conteudo(
    p_tipo_conteudo tipo_conteudo_enum,
    p_conteudo_id bigint,
    p_autor_id bigint,
    p_score_risco numeric,
    p_justificativa text default null
)
returns bigint as $$
declare
    v_status status_moderacao_enum;
    v_moderacao_id bigint;
begin
    -- Define o status com base no limiar de risco da IA/NLP
    if p_score_risco >= 0.7 then
        v_status := 'bloqueado';
    elsif p_score_risco >= 0.4 then
        v_status := 'sob analise';
    else
        v_status := 'aprovado';
    end if;

    insert into moderacao_conteudo (
        tipo_conteudo,
        conteudo_id,
        autor_id,
        status_moderacao,
        score_risco,
        justificativa_acao,
        data_analise
    ) values (
        p_tipo_conteudo,
        p_conteudo_id,
        p_autor_id,
        v_status,
        p_score_risco,
        p_justificativa,
        current_timestamp
    )
    returning id into v_moderacao_id;

    return v_moderacao_id;
end;
$$ language plpgsql;