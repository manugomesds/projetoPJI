CREATE TABLE comunidades (
    id BIGSERIAL PRIMARY KEY,
    criador_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    nome VARCHAR(100) NOT NULL UNIQUE,
    descricao TEXT NOT NULL,
    categoria_artistica VARCHAR(100) NOT NULL,
    privacidade privacidade_comunidade_enum DEFAULT 'publica',
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE membros_comunidade (
    comunidade_id BIGINT REFERENCES comunidades(id) ON DELETE CASCADE,
    usuario_id BIGINT REFERENCES usuarios(id) ON DELETE CASCADE,
    papel papel_comunidade_enum DEFAULT 'membro',
    aprovado BOOLEAN DEFAULT TRUE,
    data_ingresso TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (comunidade_id, usuario_id)
);

CREATE TABLE editais (
    id BIGSERIAL PRIMARY KEY,
    comunidade_id BIGINT NOT NULL REFERENCES comunidades(id) ON DELETE CASCADE,
    publicador_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    titulo VARCHAR(150) NOT NULL,
    descricao TEXT NOT NULL,
    url_arquivo_oficial VARCHAR(255) NOT NULL,
    data_inicio_inscricao DATE NOT NULL,
    data_fim_inscricao DATE NOT NULL,
    data_resultado DATE NOT NULL,
    data_publicacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE retificacoes_edital (
    id BIGSERIAL PRIMARY KEY,
    edital_id BIGINT NOT NULL REFERENCES editais(id) ON DELETE CASCADE,
    titulo_retificacao VARCHAR(150) NOT NULL,
    descricao_alteracoes TEXT NOT NULL,
    url_arquivo_aditivo VARCHAR(255) NOT NULL,
    data_retificacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);