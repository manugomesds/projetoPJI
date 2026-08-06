CREATE TABLE denuncias_plagio (
    id BIGSERIAL PRIMARY KEY,
    denunciante_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    perfil_denunciado_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo_violacao tipo_violacao_enum NOT NULL,
    descricao_detalhada TEXT NOT NULL,
    url_prova_plagio VARCHAR(255),
    status_denuncia status_denuncia_enum DEFAULT 'recebida',
    medidas_adotadas TEXT,
    documento_suporte_url VARCHAR(255),
    data_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ultima_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE moderacao_conteudo (
    id BIGSERIAL PRIMARY KEY,
    tipo_conteudo tipo_conteudo_enum NOT NULL,
    conteudo_id BIGINT NOT NULL,
    autor_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    status_moderacao status_moderacao_enum DEFAULT 'sob analise',
    score_risco NUMERIC(3,2) DEFAULT 0.00,
    justificativa_acao TEXT,
    data_analise TIMESTAMP,
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reportes_usuario (
    id BIGSERIAL PRIMARY KEY,
    denunciante_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo_conteudo tipo_conteudo_enum NOT NULL,
    conteudo_id BIGINT NOT NULL,
    motivo_reporte VARCHAR(150) NOT NULL,
    descricao_adicional TEXT,
    data_reporte TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE itens_salvos (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo_alvo tipo_alvo_salvo_enum NOT NULL,
    alvo_id BIGINT NOT NULL,
    data_salvamento TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT salvo_unico UNIQUE (usuario_id, tipo_alvo, alvo_id)
);