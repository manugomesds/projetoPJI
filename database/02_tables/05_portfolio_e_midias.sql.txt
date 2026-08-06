CREATE TABLE portfolio_arquivos (
    id BIGSERIAL PRIMARY KEY,
    artista_id BIGINT NOT NULL REFERENCES perfis_artistas(usuario_id) ON DELETE CASCADE,
    url_arquivo VARCHAR(255) NOT NULL,
    nome_original VARCHAR(150) NOT NULL,
    tamanho_bytes INTEGER NOT NULL,
    tipo_mime VARCHAR(50) NOT NULL,
    data_upload TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    possui_selo BOOLEAN DEFAULT FALSE,
    hash_sha256 CHAR(64) UNIQUE,
    data_selo TIMESTAMP
);

CREATE TABLE embeds_externos (
    id BIGSERIAL PRIMARY KEY,
    artista_id BIGINT NOT NULL REFERENCES perfis_artistas(usuario_id) ON DELETE CASCADE,
    url_original VARCHAR(255) NOT NULL,
    codigo_iframe TEXT NOT NULL,
    tipo_midia tipo_midia_enum NOT NULL,
    legenda VARCHAR(255),
    ordem_exibicao INTEGER DEFAULT 0
);