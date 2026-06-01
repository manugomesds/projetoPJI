-- Tipos ENUM (inalterados)
create type tipo_usuario_enum as enum ('contratante', 'artista');
create type modelo_trabalho_enum as enum ('presencial', 'remoto', 'hibrido');
create type status_vaga_enum as enum ('aberta', 'pausada', 'encerrada');
create type status_candidatura_enum as enum ('pendente', 'em analise', 'aprovado', 'rejeitado');
create type status_denuncia_enum as enum ('recebida', 'em analise', 'procedente', 'improcedente');
create type tipo_violacao_enum as enum ('plagio de imagem', 'plagio de audio', 'copia de biografia', 'outro');
create type tipo_midia_enum as enum ('video', 'audio', 'post social');
create type privacidade_comunidade_enum as enum ('publica', 'privada');
create type papel_comunidade_enum as enum ('membro', 'moderador', 'admin');
create type tipo_galeria_enum as enum ('individual', 'comunitaria');
create type status_galeria_enum as enum ('ativa', 'agendada', 'encerrada');
create type tipo_notificacao_enum as enum ('candidatura', 'mensagem', 'convite', 'edital', 'salvo');
create type tipo_conteudo_enum as enum ('vaga', 'comunidade', 'galeria', 'mensagem');
create type status_moderacao_enum as enum ('aprovado', 'bloqueado', 'sob analise');
create type tipo_alvo_salvo_enum as enum ('artista', 'obra', 'vaga');

-- 2. tabela de usuarios (inalterada, PK já é bigserial)
create table usuarios (
    id bigserial primary key,
    nome varchar(150) not null,
    data_nascimento date not null,
    telefone varchar(20) not null,
    email varchar(150) unique not null,
    senha varchar(255) not null,
    tipo_usuario tipo_usuario_enum not null,
    perfil_completo boolean default false,
    token_recuperacao varchar(255),
    token_expiracao timestamp,
    data_criacao timestamp default current_timestamp,
    nome_responsavel varchar(150),
    telefone_responsavel varchar(20),
    email_responsavel varchar(150)
);

-- 3. perfis_artistas (FK/PK alterada para bigint)
create table perfis_artistas (
    usuario_id bigint primary key references usuarios(id) on delete cascade,  -- ALTERADO
    biografia text,
    localizacao varchar(150),
    url_portfolio varchar(255),
    nivel_medalha integer default 1 check (nivel_medalha between 1 and 5),  -- permanece integer
    score_engajamento numeric(5,2) default 0.00,
    banner_url varchar(255),
    ultima_atualizacao timestamp default current_timestamp
);

-- perfis_contratantes (FK/PK alterada)
create table perfis_contratantes (
    usuario_id bigint primary key references usuarios(id) on delete cascade,  -- ALTERADO
    nome_empresa varchar(150),
    biografia text,
    localizacao varchar(150),
    banner_url varchar(255)
);

-- tags (inalterada, id bigserial)
create table tags (
    id bigserial primary key,
    nome varchar(50) unique not null
);

-- tags_artista (duas FKs alteradas)
create table tags_artista (
    artista_id bigint references perfis_artistas(usuario_id) on delete cascade,  -- ALTERADO
    tag_id bigint references tags(id) on delete cascade,                        -- ALTERADO
    primary key (artista_id, tag_id)
);

-- vagas (FK contratante_id alterada)
create table vagas (
    id bigserial primary key,
    contratante_id bigint not null references perfis_contratantes(usuario_id) on delete cascade,  -- ALTERADO
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

-- tags_vaga (FKs alteradas)
create table tags_vaga (
    vaga_id bigint references vagas(id) on delete cascade,   -- ALTERADO
    tag_id bigint references tags(id) on delete cascade,     -- ALTERADO
    primary key (vaga_id, tag_id)
);

-- candidaturas (FKs alteradas)
create table candidaturas (
    id bigserial primary key,
    vaga_id bigint not null references vagas(id) on delete cascade,                 -- ALTERADO
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,  -- ALTERADO
    mensagem_apresentacao text not null,
    link_portfolio_candidatura varchar(255) not null,
    status status_candidatura_enum default 'pendente',
    data_candidatura timestamp default current_timestamp,
    constraint candidatura_unica unique (vaga_id, artista_id)
);

-- visualizacoes_perfil (FK alterada)
create table visualizacoes_perfil (
    id bigserial primary key,
    perfil_visitado_id bigint not null references usuarios(id) on delete cascade,  -- ALTERADO
    data_visualizacao timestamp default current_timestamp
);

-- portfolio_arquivos (FK alterada; tamanho_bytes permanece integer)
create table portfolio_arquivos (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,  -- ALTERADO
    url_arquivo varchar(255) not null,
    nome_original varchar(150) not null,
    tamanho_bytes integer not null,        -- NÃO alterado
    tipo_mime varchar(50) not null,
    data_upload timestamp default current_timestamp,
    possui_selo boolean default false,
    hash_sha256 char(64) unique,
    data_selo timestamp
);

-- denuncias_plagio (FKs alteradas)
create table denuncias_plagio (
    id bigserial primary key,
    denunciante_id bigint not null references usuarios(id) on delete cascade,      -- ALTERADO
    perfil_denunciado_id bigint not null references usuarios(id) on delete cascade, -- ALTERADO
    tipo_violacao tipo_violacao_enum not null,
    descricao_detalhada text not null,
    url_prova_plagio varchar(255),
    status_denuncia status_denuncia_enum default 'recebida',
    medidas_adotadas text,
    documento_suporte_url varchar(255),
    data_registro timestamp default current_timestamp,
    ultima_atualizacao timestamp default current_timestamp
);

-- embeds_externos (FK alterada; ordem_exibicao permanece integer)
create table embeds_externos (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,  -- ALTERADO
    url_original varchar(255) not null,
    codigo_iframe text not null,
    tipo_midia tipo_midia_enum not null,
    legenda varchar(255),
    ordem_exibicao integer default 0  -- NÃO alterado
);

-- comunidades (FK alterada)
create table comunidades (
    id bigserial primary key,
    criador_id bigint not null references usuarios(id) on delete cascade,  -- ALTERADO
    nome varchar(100) not null unique,
    descricao text not null,
    categoria_artistica varchar(100) not null,
    privacidade privacidade_comunidade_enum default 'publica',
    data_criacao timestamp default current_timestamp
);

-- membros_comunidade (FKs alteradas)
create table membros_comunidade (
    comunidade_id bigint references comunidades(id) on delete cascade,  -- ALTERADO
    usuario_id bigint references usuarios(id) on delete cascade,        -- ALTERADO
    papel papel_comunidade_enum default 'membro',
    aprovado boolean default true,
    data_ingresso timestamp default current_timestamp,
    primary key (comunidade_id, usuario_id)
);

-- galerias_virtuais (FKs alteradas)
create table galerias_virtuais (
    id bigserial primary key,
    dono_id bigint not null references usuarios(id) on delete cascade,            -- ALTERADO
    comunidade_id bigint references comunidades(id) on delete cascade,            -- ALTERADO
    titulo varchar(150) not null,
    descricao text not null,
    categoria varchar(100) not null,
    tipo_galeria tipo_galeria_enum not null,
    status status_galeria_enum default 'ativa',
    data_inicio_agendada timestamp,
    data_criacao timestamp default current_timestamp
);

-- itens_galeria (FKs alteradas)
create table itens_galeria (
    galeria_id bigint references galerias_virtuais(id) on delete cascade,  -- ALTERADO
    arquivo_id bigint references portfolio_arquivos(id) on delete cascade, -- ALTERADO
    primary key (galeria_id, arquivo_id)
);

-- interacoes_galeria (FKs alteradas)
create table interacoes_galeria (
    id bigserial primary key,
    usuario_id bigint not null references usuarios(id) on delete cascade,            -- ALTERADO
    arquivo_id bigint not null references portfolio_arquivos(id) on delete cascade,   -- ALTERADO
    curtiu boolean default false,
    comentario text,
    data_interacao timestamp default current_timestamp
);

-- agenda_artista (FK alterada; cache_valor permanece numeric)
create table agenda_artista (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,  -- ALTERADO
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

-- editais (FKs alteradas)
create table editais (
    id bigserial primary key,
    comunidade_id bigint not null references comunidades(id) on delete cascade,  -- ALTERADO
    publicador_id bigint not null references usuarios(id) on delete cascade,      -- ALTERADO
    titulo varchar(150) not null,
    descricao text not null,
    url_arquivo_oficial varchar(255) not null,
    data_inicio_inscricao date not null,
    data_fim_inscricao date not null,
    data_resultado date not null,
    data_publicacao timestamp default current_timestamp
);

-- retificacoes_edital (FK alterada)
create table retificacoes_edital (
    id bigserial primary key,
    edital_id bigint not null references editais(id) on delete cascade,  -- ALTERADO
    titulo_retificacao varchar(150) not null,
    descricao_alteracoes text not null,
    url_arquivo_aditivo varchar(255) not null,
    data_retificacao timestamp default current_timestamp
);

-- notificacoes (FK alterada)
create table notificacoes (
    id bigserial primary key,
    usuario_destino_id bigint not null references usuarios(id) on delete cascade,  -- ALTERADO
    tipo_notificacao tipo_notificacao_enum not null,
    mensagem_alerta text not null,
    link_contexto varchar(255) not null,
    lida boolean default false,
    data_criacao timestamp default current_timestamp
);

-- salas_chat (inalterada)
create table salas_chat (
    id bigserial primary key,
    data_criacao timestamp default current_timestamp
);

-- participantes_chat (FKs alteradas)
create table participantes_chat (
    sala_id bigint references salas_chat(id) on delete cascade,   -- ALTERADO
    usuario_id bigint references usuarios(id) on delete cascade,  -- ALTERADO
    primary key (sala_id, usuario_id)
);

-- mensagens_chat (FKs alteradas)
create table mensagens_chat (
    id bigserial primary key,
    sala_id bigint not null references salas_chat(id) on delete cascade,     -- ALTERADO
    remetente_id bigint not null references usuarios(id) on delete cascade,  -- ALTERADO
    texto_mensagem text,
    url_anexo varchar(255),
    lida boolean default false,
    data_envio timestamp default current_timestamp
);

-- log_vagas_canceladas (FKs alteradas)
create table log_vagas_canceladas (
    id bigserial primary key,
    vaga_id bigint not null references vagas(id) on delete cascade,             -- ALTERADO
    cancelado_por_id bigint not null references usuarios(id) on delete cascade, -- ALTERADO
    data_cancelamento timestamp default current_timestamp
);

-- moderacao_conteudo (FK autor_id e conteudo_id polimórfico alterados; score_risco permanece numeric)
create table moderacao_conteudo (
    id bigserial primary key,
    tipo_conteudo tipo_conteudo_enum not null,
    conteudo_id bigint not null,                     -- ALTERADO (polimórfico, todas as tabelas referenciadas usam bigserial)
    autor_id bigint not null references usuarios(id) on delete cascade,  -- ALTERADO
    status_moderacao status_moderacao_enum default 'sob analise',
    score_risco numeric(3,2) default 0.00,          -- NÃO alterado
    justificativa_acao text,
    data_analise timestamp,
    data_criacao timestamp default current_timestamp
);

-- reportes_usuario (FKs alteradas)
create table reportes_usuario (
    id bigserial primary key,
    denunciante_id bigint not null references usuarios(id) on delete cascade,  -- ALTERADO
    tipo_conteudo tipo_conteudo_enum not null,
    conteudo_id bigint not null,                     -- ALTERADO (polimórfico)
    motivo_reporte varchar(150) not null,
    descricao_adicional text,
    data_reporte timestamp default current_timestamp
);

-- itens_salvos (FK e alvo_id polimórfico alterados)
create table itens_salvos (
    id bigserial primary key,
    usuario_id bigint not null references usuarios(id) on delete cascade,  -- ALTERADO
    tipo_alvo tipo_alvo_salvo_enum not null,
    alvo_id bigint not null,                     -- ALTERADO (artista=bigint, obra=bigint, vaga=bigint)
    data_salvamento timestamp default current_timestamp,
    constraint salvo_unico unique (usuario_id, tipo_alvo, alvo_id)
);

-- ranking_top_da_semana (FK alterada; posicao_ranking permanece integer)
create table ranking_top_da_semana (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,  -- ALTERADO
    score_semanal numeric(10,2) not null,
    data_inicio_ciclo date not null,
    data_fim_ciclo date not null,
    posicao_ranking integer not null,       -- NÃO alterado
    data_calculo timestamp default current_timestamp
);

-- historico_medalhas (FK alterada; niveis permanecem integer)
create table historico_medalhas (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,  -- ALTERADO
    nivel_antigo integer check (nivel_antigo between 1 and 5),   -- NÃO alterado
    nivel_novo integer not null check (nivel_novo between 1 and 5),  -- NÃO alterado
    motivo_progressao varchar(255),
    data_mudanca timestamp default current_timestamp
);

-- conquistas_desbloqueadas (FK alterada)
create table conquistas_desbloqueadas (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,  -- ALTERADO
    nome_conquista varchar(100) not null,
    descricao_conquista text not null,
    data_desbloqueio timestamp default current_timestamp
);

-- log_exclusoes_lgpd (usuario_id_antigo alterado para bigint; comprovante_hash mantido char(64))
create table log_exclusoes_lgpd (
    id bigserial primary key,
    usuario_id_antigo bigint not null,   -- ALTERADO (armazena ID de usuário, que é bigint)
    motivo_opcional text,
    data_exclusao timestamp default current_timestamp,
    comprovante_hash char(64) not null
);
