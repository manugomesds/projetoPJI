create or replace function fn_filtrar_banco_talentos(
    p_termo varchar default null,
    p_area_id smallint default null,
    p_funcao_id bigint default null,
    p_especializacao_id bigint default null,
    p_cidade varchar default null,
    p_estado varchar default null,
    p_nivel_medalha integer default null,
    p_limit integer default 20,
    p_cursor_area_nome varchar default null,
    p_cursor_funcao_nome varchar default null,
    p_cursor_especializacao_nome varchar default null,
    p_cursor_id bigint default null
)
returns table (
    artista_id bigint,
    nome_artista varchar,
    area_nome varchar,
    funcao_nome varchar,
    especializacao_nome varchar,
    biografia text,
    cidade varchar,
    estado varchar,
    nivel_medalha integer
) as $$
begin
    return query
    select distinct on (aa.nome, coalesce(f.nome, ''), coalesce(e.nome, ''), u.id)
        u.id as artista_id,
        u.nome as nome_artista,
        aa.nome as area_nome,
        coalesce(f.nome, 'N/A') as funcao_nome,
        coalesce(e.nome, 'N/A') as especializacao_nome,
        p.biografia,
        p.cidade,
        p.estado,
        p.nivel_medalha
    from perfis_artistas p
    join usuarios u on p.usuario_id = u.id
    join perfil_artista_area paa on p.usuario_id = paa.perfil_artista_id and paa.principal = true
    join areas_artisticas aa on paa.area_id = aa.id
    left join perfil_artista_funcao paf on p.usuario_id = paf.perfil_artista_id and paa.area_id = paf.area_id
    left join funcoes f on paf.funcao_id = f.id
    left join perfil_artista_especializacao pae on p.usuario_id = pae.perfil_artista_id and paa.area_id = pae.area_id
    left join especializacoes e on pae.especializacao_id = e.id
    where u.perfil_completo = true
      and u.status_conta = 'ATIVO'
      and (p_termo is null or u.nome ilike '%' || p_termo || '%' or p.biografia ilike '%' || p_termo || '%')
      and (p_area_id is null or paa.area_id = p_area_id)
      and (p_funcao_id is null or paf.funcao_id = p_funcao_id)
      and (p_especializacao_id is null or pae.especializacao_id = p_especializacao_id)
      and (p_cidade is null or p.cidade ilike '%' || p_cidade || '%')
      and (p_estado is null or p.estado = p_estado)
      and (p_nivel_medalha is null or p.nivel_medalha >= p_nivel_medalha)
      
      -- paginação por cursor (keyset)
      and (
          p_cursor_id is null 
          or (aa.nome, coalesce(f.nome, ''), coalesce(e.nome, ''), u.id) > 
             (p_cursor_area_nome, coalesce(p_cursor_funcao_nome, ''), coalesce(p_cursor_especializacao_nome, ''), p_cursor_id)
      )
    order by 
        aa.nome asc, 
        coalesce(f.nome, '') asc, 
        coalesce(e.nome, '') asc, 
        u.id asc
    limit greatest(1, least(p_limit, 100));
end;
$$ language plpgsql;