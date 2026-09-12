-- Alimenta o feed principal com as obras mais recentes postadas pelos artistas
select 
    pa.id as arquivo_id,
    pa.url_arquivo,
    pa.nome_original,
    pa.data_upload,
    pa.possui_selo,
    u.id as artista_id,
    u.nome as nome_artista,
    u.foto_perfil as foto_artista,
    p.nivel_medalha,
    p.score_engajamento
from portfolio_arquivos pa
join perfis_artistas p on pa.artista_id = p.usuario_id
join usuarios u on p.usuario_id = u.id
order by pa.data_upload desc
limit 50;