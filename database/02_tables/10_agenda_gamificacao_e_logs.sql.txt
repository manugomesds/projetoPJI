CREATE TABLE agenda_artista (
    id BIGSERIAL PRIMARY KEY,
    artista_id BIGINT NOT NULL REFERENCES perfis_artistas(usuario_id) ON DELETE CASCADE,
    titulo_compromisso VARCHAR(150) NOT NULL,
    descricao_compromisso TEXT,
    tipo_compromisso VARCHAR(50) NOT NULL,
    data_hora_inicio TIMESTAMP NOT NULL,
    data_hora_fim TIMESTAMP NOT NULL,
    localizacao_logistica VARCHAR(255) NOT NULL,
    contato_responsavel VARCHAR(150),
    cache_valor NUMERIC(10,2),
    necessidades_tecnicas TEXT,
    exibir_publico BOOLEAN DEFAULT FALSE,
    CONSTRAINT sem_conflito_horario UNIQUE (artista_id, data_hora_inicio)
);

CREATE TABLE ranking_top_da_semana (
    id BIGSERIAL PRIMARY KEY,
    artista_id BIGINT NOT NULL REFERENCES perfis_artistas(usuario_id) ON DELETE CASCADE,
    score_semanal NUMERIC(10,2) NOT NULL,
    data_inicio_ciclo DATE NOT NULL,
    data_fim_ciclo DATE NOT NULL,
    posicao_ranking INTEGER NOT NULL,
    data_calculo TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE historico_medalhas (
    id BIGSERIAL PRIMARY KEY,
    artista_id BIGINT NOT NULL REFERENCES perfis_artistas(usuario_id) ON DELETE CASCADE,
    nivel_antigo INTEGER CHECK (nivel_antigo BETWEEN 1 AND 5),
    nivel_novo INTEGER NOT NULL CHECK (nivel_novo BETWEEN 1 AND 5),
    motivo_progressao VARCHAR(255),
    data_mudanca TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE conquistas_desbloqueadas (
    id BIGSERIAL PRIMARY KEY,
    artista_id BIGINT NOT NULL REFERENCES perfis_artistas(usuario_id) ON DELETE CASCADE,
    nome_conquista VARCHAR(100) NOT NULL,
    descricao_conquista TEXT NOT NULL,
    data_desbloqueio TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE log_exclusoes_lgpd (
    id BIGSERIAL PRIMARY KEY,
    usuario_id_antigo BIGINT NOT NULL,
    motivo_opcional TEXT,
    data_exclusao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    comprovante_hash CHAR(64) NOT NULL
);