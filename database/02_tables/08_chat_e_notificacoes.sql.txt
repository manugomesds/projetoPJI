CREATE TABLE notificacoes (
    id BIGSERIAL PRIMARY KEY,
    usuario_destino_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo_notificacao tipo_notificacao_enum NOT NULL,
    mensagem_alerta TEXT NOT NULL,
    link_contexto VARCHAR(255) NOT NULL,
    lida BOOLEAN DEFAULT FALSE,
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE salas_chat (
    id BIGSERIAL PRIMARY KEY,
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE participantes_chat (
    sala_id BIGINT REFERENCES salas_chat(id) ON DELETE CASCADE,
    usuario_id BIGINT REFERENCES usuarios(id) ON DELETE CASCADE,
    PRIMARY KEY (sala_id, usuario_id)
);

CREATE TABLE mensagens_chat (
    id BIGSERIAL PRIMARY KEY,
    sala_id BIGINT NOT NULL REFERENCES salas_chat(id) ON DELETE CASCADE,
    remetente_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    texto_mensagem TEXT,
    url_anexo VARCHAR(255),
    lida BOOLEAN DEFAULT FALSE,
    data_envio TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);