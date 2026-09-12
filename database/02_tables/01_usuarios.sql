create table usuarios (
	id bigserial primary key,
    nome varchar(150) not null,
    data_nascimento date not null,
    telefone varchar(20) not null,
    email varchar(150) unique not null,
    senha varchar(255),
    
    google_id varchar(255) unique,
    foto_perfil_url varchar(255),
    tipo_usuario tipo_usuario_enum not null,
    cpf varchar(14) unique,
    cnpj varchar(14) unique,
    
    perfil_completo boolean default false,
    status_conta status_conta_enum not null,
    versao_termo varchar(50),
    
    email_verificado boolean default false,
    token_verificacao varchar(255),
    token_recuperacao varchar(255),
    token_expiracao timestamp,
    tentativas_verificacao_email integer default 0,
    ultimo_reenvio_verificacao timestamp,
    data_criacao timestamp default current_timestamp,

    constraint chk_auth_method check (senha is not null or google_id is not null)
);

-- Tabela normalizada para dados de responsáveis legais (RF27)
create table responsaveis_legais (
    id bigserial primary key,
    usuario_id bigint unique references usuarios(id) on delete set null,
    nome_responsavel varchar(150) not null,
    telefone_responsavel varchar(20) not null,
    versao_termo varchar(50),
    email_responsavel varchar(150) not null,
    token_consentimento varchar(255),
    consentimento_revogado boolean default false,
    data_consentimento timestamp
);

create table refresh_tokens (
    id bigserial primary key,
    usuario_id bigint references usuarios(id) on delete set null,
    token_hash varchar(64) not null unique,
    expiracao timestamp not null,
    ativo boolean not null default true,
	dispositivo_info varchar(255),
    ip_criacao varchar(45),
    ultimo_uso timestamp,
    data_criacao timestamp default current_timestamp
);


create index if not exists idx_refresh_tokens_usuario on refresh_tokens(usuario_id);