create or replace function fn_buscar_vagas(
    p_termo varchar default null,
    p_categoria varchar default null,
    p_cidade varchar default null,
    p_estado varchar default null,
    p_modelo_trabalho modelo_trabalho_enum default null
)
returns table (
    vaga_id bigint,
    v_contratante_id bigint,
    v_titulo varchar,
    v_cidade varchar,
    v_estado varchar,
    v_modelo_trabalho modelo_trabalho_enum,
    v_remunera_valor numeric,
    v_data_publicacao timestamp
) as $$
begin
    return query
    select 
        v.id, 
        v.contratante_id, 
        v.titulo, 
        v.cidade, 
        v.estado, 
        v.modelo_trabalho, 
        v.remunera_valor, 
        v.data_publicacao
    from vagas v
    where v.status = 'ABERTA'
      and (p_termo is null or v.titulo ilike '%' || p_termo || '%' or v.descricao ilike '%' || p_termo || '%')
      and (p_categoria is null or v.categoria = p_categoria)
      and (p_cidade is null or v.cidade ilike '%' || p_cidade || '%')
      and (p_estado is null or v.estado = p_estado)
      and (p_modelo_trabalho is null or v.modelo_trabalho = p_modelo_trabalho);
end;
$$ language plpgsql;