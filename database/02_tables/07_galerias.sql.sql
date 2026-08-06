CREATE TABLE galerias_virtuais (
    id BIGSERIAL PRIMARY KEY,
    dono_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    comunidade_id BIGINT REFERENCES comunidades(id) ON DELETE CASCADE,
    titulo VARCHAR(150) NOT NULL,
    descricao TEXT NOT NULL,
    categoria VARCHAR(100) NOT NULL,
    tipo_galeria tipo_galeria_enum NOT NULL,
    status status_galeria_enum DEFAULT 'ativa',
    data_inicio_agendada TIMESTAMP,
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE itens_galeria (
    galeria_id BIGINT REFERENCES galerias_virtuais(id) ON DELETE CASCADE,
    arquivo_id BIGINT REFERENCES portfolio_arquivos(id) ON DELETE CASCADE,
    PRIMARY KEY (galeria_id, arquivo_id)
);

CREATE TABLE interacoes_galeria (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    arquivo_id BIGINT NOT NULL REFERENCES portfolio_arquivos(id) ON DELETE CASCADE,
    curtiu BOOLEAN DEFAULT FALSE,
    comentario TEXT,
    data_interacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);