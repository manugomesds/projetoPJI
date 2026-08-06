CREATE TABLE tags (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE tags_artista (
    artista_id BIGINT REFERENCES perfis_artistas(usuario_id) ON DELETE CASCADE,
    tag_id BIGINT REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (artista_id, tag_id)
);