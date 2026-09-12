create or replace function fn_buscar_vagas(
    p_termo varchar default null,
    p_cidade varchar default null,
    p_estado varchar default null,
    p_area_id smallint default null,
    p_funcoes_ids bigint[] default null,
    p_especializacoes_ids bigint[] default null,
    p_categorias_afirmativas_ids integer[] default null,
    p_modelo_trabalho modelo_trabalho_enum default null,
    p_abrangencia abrangencia_enum default null,
    p_experiencia varchar default null,
    p_valor_minimo numeric default null,
    p_valor_maximo numeric default null,
    p_limit integer default 20,
    p_cursor_data_publicacao timestamp default null,
    p_cursor_id bigint default null
)
returns table (
    vaga_id bigint,
    v_contratante_id bigint,
    v_area_id smallint,
    v_titulo varchar,
    v_cidade varchar,
    v_estado varchar,
    v_modelo_trabalho modelo_trabalho_enum,
    v_abrangencia abrangencia_enum,
    v_experiencia varchar,
    v_forma_remuneracao forma_remuneracao_enum,
    v_valor_minimo numeric,
    v_valor_maximo numeric,
    v_data_publicacao timestamp
) as $$
begin
    return query
    select 
        v.id, 
        v.contratante_id, 
        v.area_id,
        v.titulo, 
        v.cidade, 
        v.estado, 
        v.modelo_trabalho, 
        v.abrangencia,
        v.experiencia,
        v.forma_remuneracao,
        v.valor_minimo,
        v.valor_maximo,
        v.data_publicacao
    from vagas v
    where v.status = 'ABERTA'
      and (p_termo is null or v.titulo ilike '%' || p_termo || '%' or v.descricao ilike '%' || p_termo || '%')
      and (p_cidade is null or v.cidade ilike '%' || p_cidade || '%')
      and (p_estado is null or v.estado = p_estado)
      and (p_area_id is null or v.area_id = p_area_id)
      
      -- relação n:n com funções
      and (
          p_funcoes_ids is null 
          or exists (
              select 1 from vaga_funcao vf 
              where vf.vaga_id = v.id and vf.funcao_id = any(p_funcoes_ids)
          )
      )
      
      -- relação n:n com especializações
      and (
          p_especializacoes_ids is null 
          or exists (
              select 1 from vaga_especializacao ve 
              where ve.vaga_id = v.id and ve.especializacao_id = any(p_especializacoes_ids)
          )
      )
      
      -- relação n:n com categorias afirmativas
      and (
          p_categorias_afirmativas_ids is null 
          or exists (
              select 1 from vagas_categorias_afirmativas vca 
              where vca.vaga_id = v.id and vca.categoria_id = any(p_categorias_afirmativas_ids)
          )
      )
      
      and (p_modelo_trabalho is null or v.modelo_trabalho = p_modelo_trabalho)
      and (p_abrangencia is null or v.abrangencia = p_abrangencia)
      and (p_experiencia is null or v.experiencia ilike '%' || p_experiencia || '%')
      and (p_valor_minimo is null or v.valor_maximo >= p_valor_minimo or v.forma_remuneracao = 'A_COMBINAR')
      and (p_valor_maximo is null or v.valor_minimo <= p_valor_maximo or v.forma_remuneracao = 'A_COMBINAR')
      
      -- paginação por cursor (keyset)
      and (
          p_cursor_data_publicacao is null 
          or p_cursor_id is null 
          or (v.data_publicacao, v.id) < (p_cursor_data_publicacao, p_cursor_id)
      )
    order by v.data_publicacao desc, v.id desc
    limit greatest(1, least(p_limit, 100));
end;
$$ language plpgsql;