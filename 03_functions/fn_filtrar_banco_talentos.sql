create or replace function fn_filtrar_banco_talentos(
    p_tag_id bigint default null,
    p_localizacao varchar default null,
    p_nivel_medalha integer default null
)
returns table (
    artista_id bigint,
    nome_artista varchar,
    biografia text,
    localizacao varchar,
    nivel_medalha integer,
    score_engajamento numeric
) as $$
begin
    return query
    select distinct
        u.id,
        u.nome,
        p.biografia,
        p.localizacao,
        p.nivel_medalha,
        p.score_engajamento
    from perfis_artistas p
    join usuarios u on p.usuario_id = u.id
    left join tags_artista ta on p.usuario_id = ta.artista_id
    where (p_tag_id is null or ta.tag_id = p_tag_id)
      and (p_localizacao is null or p.localizacao ilike '%' || p_localizacao || '%')
      and (p_nivel_medalha is null or p.nivel_medalha >= p_nivel_medalha)
    order by p.score_engajamento desc;
end;
$$ language plpgsql;