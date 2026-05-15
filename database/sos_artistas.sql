
-- 1. enums corrigidos totalmente para minusculas
create type tipo_usuario_enum as enum ('contratante', 'artista');
create type modelo_trabalho_enum as enum ('presencial', 'remoto', 'hibrido');
create type status_vaga_enum as enum ('aberta', 'pausada', 'encerrada');
create type status_candidatura_enum as enum ('pendente', 'em analise', 'aprovado', 'rejeitado');

-- 2. tabela de usuarios (geral - rf01, rf02, rf08, rf09, rf12)
create table usuarios (
    id serial primary key,
    nome varchar(150) not null,
    data_nascimento date not null,
    telefone varchar(20) not null,
    email varchar(150) unique not null,
    senha varchar(255) not null, -- armazenara o hash da senha
    tipo_usuario tipo_usuario_enum not null,
    perfil_completo boolean default false,
    token_recuperacao varchar(255),
    token_expiracao timestamp,
    data_criacao timestamp default current_timestamp,
    
    nome_responsavel varchar(150),
    telefone_responsavel varchar(20),
    email_responsavel varchar(150)
);

-- 3. (rf05, rf08, rf10, rf11, rf13)
create table perfis_artistas (
    usuario_id integer primary key references usuarios(id) on delete cascade,
    biografia text,
    localizacao varchar(150),
    url_portfolio varchar(255),
    nivel_medalha integer default 1 check (nivel_medalha between 1 and 5),
    score_engajamento numeric(5,2) default 0.00,
    banner_url varchar(255),
    ultima_atualizacao timestamp default current_timestamp
);

create table perfis_contratantes (
    usuario_id integer primary key references usuarios(id) on delete cascade,
    nome_empresa varchar(150),
    biografia text,
    localizacao varchar(150),
    banner_url varchar(255)
);

-- (rf03, rf04, rf05, rf08, rf10)
create table tags (
    id serial primary key,
    nome varchar(50) unique not null
);


create table tags_artista (
    artista_id integer references perfis_artistas(usuario_id) on delete cascade,
    tag_id integer references tags(id) on delete cascade,
    primary key (artista_id, tag_id)
);

-- (rf03, rf04, rf05, rf07)
create table vagas (
    id serial primary key,
    contratante_id integer not null references perfis_contratantes(usuario_id) on delete cascade,
    titulo varchar(150) not null,
    descricao text not null,
    requisitos text not null,
    remunera_valor numeric(10, 2) not null, 
    forma_pagamento varchar(100) not null,
    cidade varchar(100) not null,
    estado varchar(2) not null,
    endereco_completo text,                 
    beneficios text,                        
    modelo_trabalho modelo_trabalho_enum not null,
    tipo_contrato varchar(100) not null,     
    status status_vaga_enum default 'aberta',
    data_publicacao timestamp default current_timestamp
);

--(rf04, rf07)
create table tags_vaga (
    vaga_id integer references vagas(id) on delete cascade,
    tag_id integer references tags(id) on delete cascade,
    primary key (vaga_id, tag_id)
);

-- (rf05, rf06)
create table candidaturas (
    id serial primary key,
    vaga_id integer not null references vagas(id) on delete cascade,
    artista_id integer not null references perfis_artistas(usuario_id) on delete cascade,
    mensagem_apresentacao text not null,
    link_portfolio_candidatura varchar(255) not null,
    status status_candidatura_enum default 'pendente',
    data_candidatura timestamp default current_timestamp,
    
    -- (rf06)
    constraint candidatura_unica unique (vaga_id, artista_id)
);

-- rf10, rf13)
create table visualizacoes_perfil (
    id serial primary key,
    perfil_visitado_id integer not null references usuarios(id) on delete cascade,
    data_visualizacao timestamp default current_timestamp
);