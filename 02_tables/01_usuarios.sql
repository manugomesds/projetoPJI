create table usuarios (
    id bigserial primary key,
    nome varchar(150) not null,
    data_nascimento date not null,
    telefone varchar(20) not null,
    email varchar(150) unique not null,
    email_verificado boolean default false,
    senha varchar(255),
    google_id varchar(255) unique,
    foto_perfil varchar(255),
    tipo_usuario tipo_usuario_enum not null,
    perfil_completo boolean default false,
    token_verificacao VARCHAR(255),
    token_recuperacao varchar(255),
    token_expiracao timestamp,
    data_criacao timestamp default current_timestamp
);

create table responsaveis_legais (
    id bigserial primary key,
    usuario_id bigint not null unique references usuarios(id) on delete cascade,
    nome_responsavel varchar(150) not null,
    telefone_responsavel varchar(20) not null,
    email_responsavel varchar(150) not null,
    consentimento_revogado boolean default false;
    token_consentimento varchar(255),
    versao_termo VARCHAR(50),
    data_consentimento timestamp
);

create table refresh_tokens (
    id bigserial primary key,
    usuario_id bigint not null references usuarios(id) on delete cascade,
    token_hash varchar(64) not null unique,
    expiracao timestamp not null,
    ativo boolean not null default true,
    data_criacao timestamp default current_timestamp
);

create index if not exists idx_refresh_tokens_usuario on refresh_tokens(usuario_id);
