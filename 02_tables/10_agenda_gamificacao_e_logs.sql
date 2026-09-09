create table agenda_artista (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,
    titulo_compromisso varchar(150) not null,
    descricao_compromisso text,
    tipo_compromisso varchar(50) not null,
    data_hora_inicio timestamp not null,
    data_hora_fim timestamp not null,
    localizacao_logistica varchar(255) not null,
    contato_responsavel varchar(150),
    cache_valor numeric(10,2),
    necessidades_tecnicas text,
    exibir_publico boolean default false,
    
    constraint sem_conflito_horario unique (artista_id, data_hora_inicio)
);

create table ranking_top_da_semana (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,
    score_semanal numeric(10,2) not null,
    data_inicio_ciclo date not null,
    data_fim_ciclo date not null,
    posicao_ranking integer not null,
    data_calculo timestamp default current_timestamp
);

create table historico_medalhas (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,
    nivel_antigo integer check (nivel_antigo between 1 and 5),
    nivel_novo integer not null check (nivel_novo between 1 and 5),
    motivo_progressao varchar(255),
    data_mudanca timestamp default current_timestamp
);

create table conquistas_desbloqueadas (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,
    nome_conquista varchar(100) not null,
    descricao_conquista text not null,
    data_desbloqueio timestamp default current_timestamp
);

create table log_exclusoes_lgpd (
    id bigserial primary key,
    motivo_opcional text,
    data_exclusao timestamp default current_timestamp,
    comprovante_hash char(64) not null
);

create index if not exists idx_agenda_artista_id on agenda_artista(artista_id);
create index if not exists idx_ranking_top_semana_artista on ranking_top_da_semana(artista_id);
create index if not exists idx_historico_medalhas_artista on historico_medalhas(artista_id);
create index if not exists idx_conquistas_artista on conquistas_desbloqueadas(artista_id);
