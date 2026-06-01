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

-- 2. tabela de usuarios (geral - rf01, rf02, rf08, rf09, rf12)
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

-- 3. (rf05, rf08, rf10, rf11, rf13)
create table perfis_artistas (
    usuario_id bigint primary key references usuarios(id) on delete cascade,
    biografia text,
    localizacao varchar(150),
    url_portfolio varchar(255),
    nivel_medalha integer default 1 check (nivel_medalha between 1 and 5),
    score_engajamento numeric(5,2) default 0.00,
    banner_url varchar(255),
    ultima_atualizacao timestamp default current_timestamp
);

create table perfis_contratantes (
    usuario_id bigint primary key references usuarios(id) on delete cascade,
    nome_empresa varchar(150),
    biografia text,
    localizacao varchar(150),
    banner_url varchar(255)
);

-- (rf03, rf04, rf05, rf08, rf10)
create table tags (
    id bigserial primary key,
    nome varchar(50) unique not null
);

create table tags_artista (
    artista_id bigint references perfis_artistas(usuario_id) on delete cascade,
    tag_id bigint references tags(id) on delete cascade,
    primary key (artista_id, tag_id)
);

-- (rf03, rf04, rf05, rf07)
create table vagas (
    id bigserial primary key,
    contratante_id bigint not null references perfis_contratantes(usuario_id) on delete cascade,
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
    vaga_id bigint references vagas(id) on delete cascade,
    tag_id bigint references tags(id) on delete cascade,
    primary key (vaga_id, tag_id)
);

-- (rf05, rf06)
create table candidaturas (
    id bigserial primary key,
    vaga_id bigint not null references vagas(id) on delete cascade,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,
    mensagem_apresentacao text not null,
    link_portfolio_candidatura varchar(255) not null,
    status status_candidatura_enum default 'pendente',
    data_candidatura timestamp default current_timestamp,

    -- (rf06)
    constraint candidatura_unica unique (vaga_id, artista_id)
);

-- rf10, rf13)
create table visualizacoes_perfil (
    id bigserial primary key,
    perfil_visitado_id bigint not null references usuarios(id) on delete cascade,
    data_visualizacao timestamp default current_timestamp
);

-- (rf15, rf16)
create table portfolio_arquivos (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,
    url_arquivo varchar(255) not null,
    nome_original varchar(150) not null,
    tamanho_bytes integer not null,
    tipo_mime varchar(50) not null,
    data_upload timestamp default current_timestamp,

    -- (rf15)
    possui_selo boolean default false,
    hash_sha256 char(64) unique,
    data_selo timestamp
);

-- (rf14)
create table denuncias_plagio (
    id bigserial primary key,
    denunciante_id bigint not null references usuarios(id) on delete cascade,
    perfil_denunciado_id bigint not null references usuarios(id) on delete cascade,
    tipo_violacao tipo_violacao_enum not null,
    descricao_detalhada text not null,
    url_prova_plagio varchar(255),
    status_denuncia status_denuncia_enum default 'recebida',
    medidas_adotadas text,
    documento_suporte_url varchar(255),
    data_registro timestamp default current_timestamp,
    ultima_atualizacao timestamp default current_timestamp
);

-- (rf18)
create table embeds_externos (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,
    url_original varchar(255) not null,
    codigo_iframe text not null,
    tipo_midia tipo_midia_enum not null,
    legenda varchar(255),
    ordem_exibicao integer default 0
);

-- (rf19)
create table comunidades (
    id bigserial primary key,
    criador_id bigint not null references usuarios(id) on delete cascade,
    nome varchar(100) not null unique,
    descricao text not null,
    categoria_artistica varchar(100) not null,
    privacidade privacidade_comunidade_enum default 'publica',
    data_criacao timestamp default current_timestamp
);

-- (rf19)
create table membros_comunidade (
    comunidade_id bigint references comunidades(id) on delete cascade,
    usuario_id bigint references usuarios(id) on delete cascade,
    papel papel_comunidade_enum default 'membro',
    aprovado boolean default true,
    data_ingresso timestamp default current_timestamp,
    primary key (comunidade_id, usuario_id)
);

-- (rf20)
create table galerias_virtuais (
    id bigserial primary key,
    dono_id bigint not null references usuarios(id) on delete cascade,
    comunidade_id bigint references comunidades(id) on delete cascade,
    titulo varchar(150) not null,
    descricao text not null,
    categoria varchar(100) not null,
    tipo_galeria tipo_galeria_enum not null,
    status status_galeria_enum default 'ativa',
    data_inicio_agendada timestamp,
    data_criacao timestamp default current_timestamp
);

-- vinculo n:n entre a galeria e as obras do portfolio
create table itens_galeria (
    galeria_id bigint references galerias_virtuais(id) on delete cascade,
    arquivo_id bigint references portfolio_arquivos(id) on delete cascade,
    primary key (galeria_id, arquivo_id)
);

--  (rf20)
create table interacoes_galeria (
    id bigserial primary key,
    usuario_id bigint not null references usuarios(id) on delete cascade,
    arquivo_id bigint not null references portfolio_arquivos(id) on delete cascade,
    curtiu boolean default false,
    comentario text,
    data_interacao timestamp default current_timestamp
);

-- (rf21)
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

    -- (rf21)
    constraint sem_conflito_horario unique (artista_id, data_hora_inicio)
);

--(rf22)
create table editais (
    id bigserial primary key,
    comunidade_id bigint not null references comunidades(id) on delete cascade,
    publicador_id bigint not null references usuarios(id) on delete cascade,
    titulo varchar(150) not null,
    descricao text not null,
    url_arquivo_oficial varchar(255) not null,
    data_inicio_inscricao date not null,
    data_fim_inscricao date not null,
    data_resultado date not null,
    data_publicacao timestamp default current_timestamp
);

--(rf22)
create table retificacoes_edital (
    id bigserial primary key,
    edital_id bigint not null references editais(id) on delete cascade,
    titulo_retificacao varchar(150) not null,
    descricao_alteracoes text not null,
    url_arquivo_aditivo varchar(255) not null,
    data_retificacao timestamp default current_timestamp
);

-- (rf23)
create table notificacoes (
    id bigserial primary key,
    usuario_destino_id bigint not null references usuarios(id) on delete cascade,
    tipo_notificacao tipo_notificacao_enum not null,
    mensagem_alerta text not null,
    link_contexto varchar(255) not null,
    lida boolean default false,
    data_criacao timestamp default current_timestamp
);

--(rf24)
create table salas_chat (
    id bigserial primary key,
    data_criacao timestamp default current_timestamp
);

--(rf24)
create table participantes_chat (
    sala_id bigint references salas_chat(id) on delete cascade,
    usuario_id bigint references usuarios(id) on delete cascade,
    primary key (sala_id, usuario_id)
);

create table mensagens_chat (
    id bigserial primary key,
    sala_id bigint not null references salas_chat(id) on delete cascade,
    remetente_id bigint not null references usuarios(id) on delete cascade,
    texto_mensagem text,
    url_anexo varchar(255),
    lida boolean default false,
    data_envio timestamp default current_timestamp
);

-- (rf25)
create table log_vagas_canceladas (
    id bigserial primary key,
    vaga_id bigint not null references vagas(id) on delete cascade,
    cancelado_por_id bigint not null references usuarios(id) on delete cascade,
    data_cancelamento timestamp default current_timestamp
);

-- (rf26)
create table moderacao_conteudo (
    id bigserial primary key,
    tipo_conteudo tipo_conteudo_enum not null,
    conteudo_id bigint not null,
    autor_id bigint not null references usuarios(id) on delete cascade,
    status_moderacao status_moderacao_enum default 'sob analise',
    score_risco numeric(3,2) default 0.00,
    justificativa_acao text,
    data_analise timestamp,
    data_criacao timestamp default current_timestamp
);

-- (rf26)
create table reportes_usuario (
    id bigserial primary key,
    denunciante_id bigint not null references usuarios(id) on delete cascade,
    tipo_conteudo tipo_conteudo_enum not null,
    conteudo_id bigint not null,
    motivo_reporte varchar(150) not null,
    descricao_adicional text,
    data_reporte timestamp default current_timestamp
);

-- (rf27)
create table itens_salvos (
    id bigserial primary key,
    usuario_id bigint not null references usuarios(id) on delete cascade,
    tipo_alvo tipo_alvo_salvo_enum not null,
    alvo_id bigint not null,
    data_salvamento timestamp default current_timestamp,
    constraint salvo_unico unique (usuario_id, tipo_alvo, alvo_id)
);

-- (rf28, rf29)
create table ranking_top_da_semana (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,
    score_semanal numeric(10,2) not null,
    data_inicio_ciclo date not null,
    data_fim_ciclo date not null,
    posicao_ranking integer not null,
    data_calculo timestamp default current_timestamp
);

-- (niveis 1 a 5 - rf29)
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

-- (exclusao de conta - rf30)
create table log_exclusoes_lgpd (
    id bigserial primary key,
    usuario_id_antigo bigint not null,
    motivo_opcional text,
    data_exclusao timestamp default current_timestamp,
    comprovante_hash char(64) not null
);
