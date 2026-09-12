-- Retorna dados cadastrais, métricas de perfil e tags agregadas de um artista
select 
    u.id as usuario_id,
    u.nome,
    u.email,
    u.telefone,
    u.foto_perfil,
    p.biografia,
    p.localizacao,
    p.url_portfolio,
    p.nivel_medalha,
    p.score_engajamento,
    p.banner_url,
    p.ultima_atualizacao,
    array_agg(t.nome) as tags
from usuarios u
join perfis_artistas p on u.id = p.usuario_id
left join tags_artista ta on p.usuario_id = ta.artista_id
left join tags t on ta.tag_id = t.id
where u.id = :artista_id
group by u.id, p.usuario_id;