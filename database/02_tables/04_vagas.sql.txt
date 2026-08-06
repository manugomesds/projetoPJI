CREATE TABLE vagas (
    id BIGSERIAL PRIMARY KEY,
    contratante_id BIGINT NOT NULL REFERENCES perfis_contratantes(usuario_id) ON DELETE CASCADE,
    titulo VARCHAR(150) NOT NULL,
    descricao TEXT NOT NULL,
    requisitos TEXT NOT NULL,
    remunera_valor NUMERIC(10, 2) NOT NULL,
    forma_pagamento VARCHAR(100) NOT NULL,
    cidade VARCHAR(100) NOT NULL,
    estado VARCHAR(2) NOT NULL,
    endereco_completo TEXT,
    beneficios TEXT,
    modelo_trabalho modelo_trabalho_enum NOT NULL,
    tipo_contrato VARCHAR(100) NOT NULL,
    status status_vaga_enum DEFAULT 'aberta',
    data_publicacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tags_vaga (
    vaga_id BIGINT REFERENCES vagas(id) ON DELETE CASCADE,
    tag_id BIGINT REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (vaga_id, tag_id)
);

CREATE TABLE candidaturas (
    id BIGSERIAL PRIMARY KEY,
    vaga_id BIGINT NOT NULL REFERENCES vagas(id) ON DELETE CASCADE,
    artista_id BIGINT NOT NULL REFERENCES perfis_artistas(usuario_id) ON DELETE CASCADE,
    mensagem_apresentacao TEXT NOT NULL,
    link_portfolio_candidatura VARCHAR(255) NOT NULL,
    status status_candidatura_enum DEFAULT 'pendente',
    data_candidatura TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT candidatura_unica UNIQUE (vaga_id, artista_id)
);

CREATE TABLE log_vagas_canceladas (
    id BIGSERIAL PRIMARY KEY,
    vaga_id BIGINT NOT NULL REFERENCES vagas(id) ON DELETE CASCADE,
    cancelado_por_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    data_cancelamento TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vagas_status_id ON vagas(status, id);
CREATE INDEX IF NOT EXISTS idx_vagas_cidade ON vagas(cidade);
CREATE INDEX IF NOT EXISTS idx_vagas_estado ON vagas(estado);
CREATE INDEX IF NOT EXISTS idx_vagas_contratante_id ON vagas(contratante_id);