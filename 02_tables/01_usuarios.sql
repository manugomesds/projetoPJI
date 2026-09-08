create table usuarios (
    id bigserial primary key,
    nome varchar(150) not null,
    data_nascimento date not null,
    telefone varchar(20) not null,
    email varchar(150) unique not null,
    senha varchar(255),
    google_id varchar(255) unique,
    foto_perfil varchar(255),
    tipo_usuario tipo_usuario_enum not null,
    perfil_completo boolean default false,
    token_recuperacao varchar(255),
    token_expiracao timestamp,
    data_criacao timestamp default current_timestamp
);

-- Tabela normalizada para dados de responsáveis legais (RF27)
create table responsaveis_legais (
    id bigserial primary key,
    usuario_id bigint not null unique references usuarios(id) on delete cascade,
    nome_responsavel varchar(150) not null,
    telefone_responsavel varchar(20) not null,
    email_responsavel varchar(150) not null,
    token_consentimento varchar(255),
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
ALTER TABLE usuarios 
ADD COLUMN IF NOT EXISTS email_verificado BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS token_verificacao VARCHAR(255);

-- RF27: Consentimento do Responsável Legal
ALTER TABLE responsaveis_legais 
ADD COLUMN IF NOT EXISTS versao_termo VARCHAR(50),
ADD COLUMN IF NOT EXISTS consentimento_revogado BOOLEAN DEFAULT FALSE;
create index if not exists idx_refresh_tokens_usuario on refresh_tokens(usuario_id);