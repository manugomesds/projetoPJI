CREATE TABLE perfis_artistas (
    usuario_id BIGINT PRIMARY KEY REFERENCES usuarios(id) ON DELETE CASCADE,
    biografia TEXT,
    foto_perfil VARCHAR(255),
    localizacao VARCHAR(150),
    url_portfolio VARCHAR(255),
    nivel_medalha INTEGER DEFAULT 1 CHECK (nivel_medalha BETWEEN 1 AND 5),
    score_engajamento NUMERIC(5,2) DEFAULT 0.00,
    banner_url VARCHAR(255),
    ultima_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE perfis_contratantes (
    usuario_id BIGINT PRIMARY KEY REFERENCES usuarios(id) ON DELETE CASCADE,
    nome_empresa VARCHAR(150),
    biografia TEXT,
    foto_perfil VARCHAR(255),
    localizacao VARCHAR(150),
    banner_url VARCHAR(255)
);

CREATE TABLE visualizacoes_perfil (
    id BIGSERIAL PRIMARY KEY,
    perfil_visitado_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    data_visualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);