create or replace function fn_verificar_status_moderacao(
    p_tipo_conteudo tipo_conteudo_enum,
    p_conteudo_id bigint
)
returns status_moderacao_enum as $$
declare
    v_status status_moderacao_enum;
begin
    select status_moderacao into v_status
    from moderacao_conteudo
    where tipo_conteudo = p_tipo_conteudo and conteudo_id = p_conteudo_id
    order by data_criacao desc
    limit 1;

    -- Se não houver registro prévio, assume como aprovado por padrão
    return coalesce(v_status, 'aprovado');
end;
$$ language plpgsql;