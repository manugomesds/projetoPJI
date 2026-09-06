create or replace function fn_sugerir_artistas_vaga(p_vaga_id bigint)
returns table (
    artista_id bigint,
    nome_artista varchar,
    tags_em_comum bigint,
    score_engajamento numeric
) as $$
begin
    return query
    select 
        u.id,
        u.nome,
        count(tv.tag_id) as tags_em_comum,
        p.score_engajamento
    from vagas v
    join tags_vaga tv on v.id = tv.vaga_id
    join tags_artista ta on tv.tag_id = ta.tag_id
    join perfis_artistas p on ta.artista_id = p.usuario_id
    join usuarios u on p.usuario_id = u.id
    where v.id = p_vaga_id
    group by u.id, u.nome, p.score_engajamento
    order by tags_em_comum desc, p.score_engajamento desc;
end;
$$ language plpgsql;