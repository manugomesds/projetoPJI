
create table comunidades (
    id bigserial primary key,
    criador_id bigint references usuarios(id) on delete set null,
    nome varchar(100) not null unique,
    descricao text not null,
    categoria_artistica varchar(100) not null,
    privacidade privacidade_comunidade_enum default 'PUBLICA',
    data_criacao timestamp default current_timestamp
);

create table membros_comunidade (
    comunidade_id bigint references comunidades(id) on delete set null,
    usuario_id bigint references usuarios(id) on delete set null,
    papel papel_comunidade_enum default 'MEMBRO',
    aprovado boolean default true,
    data_ingresso timestamp default current_timestamp,
    primary key (comunidade_id, usuario_id)
);

create table editais (
    id bigserial primary key,
    comunidade_id bigint references comunidades(id) on delete set null,
    publicador_id bigint references usuarios(id) on delete set null,
    titulo varchar(150) not null,
    descricao text not null,
    url_arquivo_oficial varchar(255) not null,
    data_inicio_inscricao date not null,
    data_fim_inscricao date not null,
    data_resultado date not null,
    data_publicacao timestamp default current_timestamp
);

create table retificacoes_edital (
    id bigserial primary key,
    edital_id bigint references editais(id) on delete set null,
    titulo_retificacao varchar(150) not null,
    descricao_alteracoes text not null,
    url_arquivo_aditivo varchar(255) not null,
    data_retificacao timestamp default current_timestamp
);

create index if not exists idx_comunidades_criador on comunidades(criador_id);
create index if not exists idx_membros_comunidade_usuario on membros_comunidade(usuario_id);
create index if not exists idx_editais_comunidade on editais(comunidade_id);
create index if not exists idx_editais_publicador on editais(publicador_id);
create index if not exists idx_retificacoes_edital_edital on retificacoes_edital(edital_id);