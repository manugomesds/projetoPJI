-- Fonte: database/01_types/01_enums.sql
create type tipo_usuario_enum as enum ('CONTRATANTE', 'ARTISTA', 'ADMIN', 'MODERADOR');
create type modelo_trabalho_enum as enum ('PRESENCIAL', 'REMOTO', 'HIBRIDO');
create type status_vaga_enum as enum ('ABERTA', 'PAUSADA', 'ENCERRADA', 'CANCELADA', 'RASCUNHO');
create type status_candidatura_enum as enum ('PENDENTE', 'EM_ANALISE', 'ACEITA', 'REJEITADA', 'RETIRADA', 'CANCELADA_POR_VAGA');
create type status_denuncia_enum as enum ('RECEBIDA', 'EM ANALISE', 'PROCEDENTE', 'IMPROCEDENTE', 'ENCERRADA');
create type tipo_violacao_enum as enum ('PLAGIO DE IMAGEM', 'PLAGIO DE AUDIO', 'COPIA DE BIOGRAFIA', 'OUTRO');
create type tipo_midia_enum as enum ('VIDEO', 'AUDIO', 'POST SOCIAL');
create type privacidade_comunidade_enum as enum ('PUBLICA', 'PRIVADA');
create type papel_comunidade_enum as enum ('MEMBRO', 'MODERADOR', 'ADMIN');
create type tipo_galeria_enum as enum ('INDIVIDUAL', 'COMUNITARIA');
create type status_galeria_enum as enum ('ATIVA', 'AGENDADA', 'ENCERRADA');
create type tipo_notificacao_enum as enum ('CANDIDATURA', 'MENSAGEM', 'CONVITE', 'EDITAL', 'SALVO');
create type tipo_conteudo_enum as enum ('VAGA', 'COMUNIDADE', 'GALERIA', 'MENSAGEM');
create type status_moderacao_enum as enum ('APROVADO', 'BLOQUEADO', 'SOB ANALISE');
create type tipo_alvo_salvo_enum as enum ('PERFIL_ARTISTA', 'OBRA', 'VAGA');
create type nivel_experiencia_enum as enum ('SEM_EXPERIENCIA','INICIANTE','INTERMEDIARIO','EXPERIENTE','ESPECIALISTA');
create type abrangencia_enum as enum ('LOCAL','REGIONAL','NACIONAL','INTERNACIONAL','REMOTO');
create type forma_remuneracao_enum as enum ('POR_HORA','DIARIA','POR_EVENTO','POR_PROJETO','MENSAL','A_COMBINAR');
create type tipo_perfil_artistico_enum as enum ('ARTISTA_SOLO','DUPLA','BANDA','GRUPO_ARTISTICO','ESTUDIO','PRODUTORA_EMPRESA');
create type tipo_contratante_enum as enum ('PESSOA_FISICA','SETOR_PUBLICO','SETOR_PRIVADO','ONG');
create type status_conta_enum as enum ('PENDENTE_VERIFICACAO_EMAIL','PENDENTE_TIPO_PERFIL','PENDENTE_CONSENTIMENTO','ATIVA','BLOQUEADA');

-- Fonte: database/02_tables/01_usuarios.sql
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
    status_conta status_conta_enum not null default 'PENDENTE_VERIFICACAO_EMAIL',
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

-- Fonte: database/02_tables/02_perfis.sql
create table perfis_artistas (
    usuario_id bigint primary key references usuarios(id) on delete cascade,
    biografia text,
    localizacao varchar(150),
    url_portfolio varchar(255),
    tipo_perfil_artistico tipo_perfil_artistico_enum not null,
    disponivel_oportunidades boolean,
    raio_atuacao  abrangencia_enum,
    nome_integrantes varchar(150),
    banner_url varchar(255),
    ultima_atualizacao timestamp default current_timestamp
);

create table perfis_contratantes (
    usuario_id bigint primary key references usuarios(id) on delete cascade,
    nome_empresa varchar(150),
    cpf varchar(14) unique,
    cnpj varchar(14) unique,
    tipo_perfil varchar(100),
    biografia text,
    localizacao varchar(150),
    banner_url varchar(255)
);

create table visualizacoes_perfil (
    id bigserial primary key,
    perfil_visitado_id bigint not null references usuarios(id) on delete cascade,
    data_visualizacao timestamp default current_timestamp
);
create index if not exists idx_visualizacoes_perfil_visitado on visualizacoes_perfil(perfil_visitado_id);

-- Fonte: database/02_tables/03_tags.sql
create table areas_artisticas (
    id smallint primary key,
    nome varchar(50) unique not null
);
-- seed fixo com as 7 áreas oficiais (RF01): Artes Cênicas, Música, Dança,
-- Artes Visuais, Audiovisual, Arte e Tecnologia, Artes Literárias.
-- Circo fica dentro de Artes Cênicas — não criar linha própria para Circo.

create table funcoes (
    id bigserial primary key,
    area_id smallint not null references areas_artisticas(id),
    nome varchar(100) not null,
    unique (area_id, nome),
    unique (id, area_id) -- necessário para o FK composto abaixo
);

create table especializacoes (
    id bigserial primary key,
    nome varchar(100) unique not null
);

-- uma especialização pode pertencer a várias funções (RF08: "uma
-- especialização pode existir em várias funções no catálogo")
create table funcao_especializacao (
    funcao_id bigint references funcoes(id) on delete cascade,
    especializacao_id bigint references especializacoes(id) on delete cascade,
    primary key (funcao_id, especializacao_id)
);

-- ARTISTA pode ter várias áreas, exatamente uma "principal"
create table perfil_artista_area (
    perfil_artista_id bigint references perfis_artistas(usuario_id) on delete cascade,
    area_id smallint references areas_artisticas(id),
    principal boolean not null default false,
    nivel_experiencia nivel_experiencia_enum,
    ultima_atualizacao timestamp default current_timestamp,
    primary key (perfil_artista_id, area_id)
);

create unique index one_principal_area_por_artista
    on perfil_artista_area(perfil_artista_id) where principal;

create table perfil_artista_funcao (
    perfil_artista_id bigint,
    area_id smallint,
    funcao_id bigint,
    primary key (perfil_artista_id, area_id, funcao_id),
    foreign key (perfil_artista_id, area_id)
        references perfil_artista_area(perfil_artista_id, area_id) on delete cascade,
    foreign key (area_id, funcao_id) references funcoes(area_id, id)
);

create table perfil_artista_especializacao (
    perfil_artista_id bigint,
    area_id smallint,
    especializacao_id bigint,
    primary key (perfil_artista_id, area_id, especializacao_id),
    foreign key (perfil_artista_id, area_id)
        references perfil_artista_area(perfil_artista_id, area_id) on delete cascade,
    foreign key (especializacao_id) references especializacoes(id)
);

-- Fonte: database/02_tables/04_vagas.sql
create table categorias_afirmativas (
    id serial primary key,
    nome varchar(100) unique not null
);

create table vagas (
    id bigserial primary key,
    contratante_id bigint not null references perfis_contratantes(usuario_id) on delete cascade,
    area_id smallint not null references areas_artisticas(id),
    titulo varchar(150) not null,
    descricao text not null,
    requisitos text not null,
    forma_remuneracao forma_remuneracao_enum not null default 'A_COMBINAR',
    valor_minimo numeric(10,2),
    valor_maximo numeric(10,2),
    cidade varchar(100) not null,
    estado varchar(2) not null,
    endereco_completo text,
    beneficios text,
    modelo_trabalho modelo_trabalho_enum,
    tipo_contrato varchar(100) not null,
    experiencia varchar(100),
    data_limite_candidatura date,
    abrangencia abrangencia_enum not null,
    status status_vaga_enum default 'RASCUNHO',
    data_publicacao timestamp default current_timestamp,

    constraint chk_valores_remuneracao check (
        (forma_remuneracao = 'A_COMBINAR')
        or (
            valor_minimo is not null
            and valor_maximo is not null
            and valor_minimo >= 0
            and valor_maximo >= 0
            and valor_minimo <= valor_maximo
        )
    )
);

create table vagas_categorias_afirmativas (
    vaga_id bigint not null references vagas(id) on delete cascade,
    categoria_id integer not null references categorias_afirmativas(id) on delete cascade,
    primary key (vaga_id, categoria_id)
);

create table fotos_vaga (
    vaga_id bigint not null references vagas(id) on delete cascade,
    ordem integer not null,
    url varchar(500) not null,
    primary key (vaga_id, ordem)
);

create table vaga_funcao (
    vaga_id bigint not null references vagas(id) on delete cascade,
    funcao_id bigint not null references funcoes(id) on delete cascade,
    primary key (vaga_id, funcao_id)
);

create table vaga_especializacao (
    vaga_id bigint not null references vagas(id) on delete cascade,
    especializacao_id bigint not null references especializacoes(id) on delete cascade,
    primary key (vaga_id, especializacao_id)
);

create table candidaturas (
    id bigserial primary key,
    vaga_id bigint not null references vagas(id) on delete cascade,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,
    mensagem_apresentacao text not null,
    link_portfolio_candidatura varchar(255) not null,
    status status_candidatura_enum default 'PENDENTE',
    data_candidatura timestamp default current_timestamp,
    constraint candidatura_unica unique (vaga_id, artista_id)
);

create table log_vagas_canceladas (
    id bigserial primary key,
    vaga_id bigint not null references vagas(id) on delete cascade,
    cancelado_por_id bigint not null references usuarios(id) on delete cascade,
    data_cancelamento timestamp default current_timestamp,
    motivo text
);


create index if not exists idx_vagas_status_id on vagas(status, id);
create index if not exists idx_vagas_cidade on vagas(cidade);
create index if not exists idx_vagas_estado on vagas(estado);
create index if not exists idx_vagas_contratante_id on vagas(contratante_id);
create index if not exists idx_candidaturas_artista on candidaturas(artista_id);
create index if not exists idx_candidaturas_vaga on candidaturas(vaga_id);
create index if not exists idx_log_vagas_canceladas_vaga on log_vagas_canceladas(vaga_id);
create index if not exists idx_log_vagas_canceladas_usuario on log_vagas_canceladas(cancelado_por_id);
create index if not exists idx_vagas_categorias_categoria on vagas_categorias_afirmativas(categoria_id);

-- Fonte: database/02_tables/05_portfolio_e_midias.sql
create table portfolio_arquivos (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,
    url_arquivo varchar(255) not null,
    nome_original varchar(150) not null,
    tamanho_bytes integer not null,
    tipo_mime varchar(50) not null,
    data_upload timestamp default current_timestamp,

    constraint chk_tipo_mime_permitido check (
        tipo_mime in ('image/jpeg', 'image/jpg', 'image/png', 'application/pdf', 'audio/mpeg')
    ),

    -- limite de tamanho por tipo, exatamente como o RF16 pede:
    -- imagem até 5 MB, PDF até 10 MB, MP3 até 20 MB
    constraint chk_tamanho_por_tipo check (
        (tipo_mime in ('image/jpeg', 'image/jpg', 'image/png')
            and tamanho_bytes > 0 and tamanho_bytes <= 5242880)
        or (tipo_mime = 'application/pdf'
            and tamanho_bytes > 0 and tamanho_bytes <= 10485760)
        or (tipo_mime = 'audio/mpeg'
            and tamanho_bytes > 0 and tamanho_bytes <= 20971520)
    )
);


create table embeds_externos (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,
    url_original varchar(255) not null,
    codigo_iframe text not null,
    tipo_midia tipo_midia_enum not null,
    legenda varchar(255),
    ordem_exibicao integer default 0
);

create index if not exists idx_portfolio_arquivos_artista on portfolio_arquivos(artista_id);
create index if not exists idx_embeds_externos_artista on embeds_externos(artista_id);

-- Fonte: database/02_tables/06_comunidades_e_editais.sql
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

-- Fonte: database/02_tables/07_galerias.sql
create table galerias_virtuais (
    id bigserial primary key,
    dono_id bigint not null references usuarios(id) on delete cascade,
    comunidade_id bigint references comunidades(id) on delete cascade,
    titulo varchar(150) not null,
    descricao text not null,
    categoria varchar(100) not null,
    tipo_galeria tipo_galeria_enum not null,
    status status_galeria_enum default 'ATIVA',
    data_inicio_agendada timestamp,
    data_criacao timestamp default current_timestamp
);

create table itens_galeria (
    galeria_id bigint references galerias_virtuais(id) on delete cascade,
    arquivo_id bigint references portfolio_arquivos(id) on delete cascade,
    primary key (galeria_id, arquivo_id)
);

create table interacoes_galeria (
    id bigserial primary key,
    usuario_id bigint not null references usuarios(id) on delete cascade,
    arquivo_id bigint not null references portfolio_arquivos(id) on delete cascade,
    curtiu boolean default false,
    comentario text,
    data_interacao timestamp default current_timestamp
);
-- No arquivo de galerias
create index if not exists idx_galerias_virtuais_dono on galerias_virtuais(dono_id);
create index if not exists idx_galerias_virtuais_comunidade on galerias_virtuais(comunidade_id);
create index if not exists idx_itens_galeria_arquivo on itens_galeria(arquivo_id);
create index if not exists idx_interacoes_galeria_usuario on interacoes_galeria(usuario_id);
create index if not exists idx_interacoes_galeria_arquivo on interacoes_galeria(arquivo_id);

-- Fonte: database/02_tables/08_chat_e_notificacoes.sql
create table notificacoes (
    id bigserial primary key,
    usuario_destino_id bigint not null references usuarios(id) on delete cascade,
    tipo_notificacao tipo_notificacao_enum not null,
    mensagem_alerta text not null,
    link_contexto varchar(255) not null,
    lida boolean default false,
    data_criacao timestamp default current_timestamp
);

create table salas_chat (
    id bigserial primary key,
    data_criacao timestamp default current_timestamp
);

create table participantes_chat (
    sala_id bigint not null references salas_chat(id) on delete cascade,
    usuario_id bigint not null references usuarios(id) on delete cascade,
    primary key (sala_id, usuario_id)
);

create table mensagens_chat (
    id bigserial primary key,
    sala_id bigint not null references salas_chat(id) on delete cascade,
    remetente_id bigint references usuarios(id) on delete set null,
    texto_mensagem text,
    url_anexo varchar(255),
    lida boolean default false,
    data_envio timestamp default current_timestamp,
	editada boolean default false,
    data_edicao timestamp,
    texto_original text,
    excluida boolean default false,
    data_exclusao timestamp,

    constraint chk_conteudo_mensagem check (
        texto_mensagem is not null or url_anexo is not null
    )
);


create index if not exists idx_notificacoes_destino_lida on notificacoes(usuario_destino_id, lida);
create index if not exists idx_participantes_chat_usuario on participantes_chat(usuario_id);
create index if not exists idx_mensagens_chat_sala_envio on mensagens_chat(sala_id, data_envio desc);
create index if not exists idx_mensagens_chat_remetente on mensagens_chat(remetente_id);

-- Fonte: database/02_tables/09_moderacao_e_engajamento.sql
create table denuncias_plagio (
    id bigserial primary key,
    denunciante_id bigint not null references usuarios(id) on delete cascade,
    perfil_denunciado_id bigint not null references usuarios(id) on delete cascade,
    tipo_violacao tipo_violacao_enum not null,
    descricao_detalhada text not null,
    url_prova_plagio varchar(255),
    status_denuncia status_denuncia_enum default 'RECEBIDA',
    medidas_adotadas text,
    documento_suporte_url varchar(255),
    data_registro timestamp default current_timestamp,
    ultima_atualizacao timestamp default current_timestamp
);

create table moderacao_conteudo (
    id bigserial primary key,
    tipo_conteudo tipo_conteudo_enum not null,
    conteudo_id bigint not null,
    moderador_id bigint references usuarios(id),
    contestacao text,
    autor_id bigint not null references usuarios(id) on delete cascade,
    status_moderacao status_moderacao_enum default 'SOB ANALISE',
    score_risco numeric(3,2) default 0.00,
    justificativa_acao text,
    data_analise timestamp,
    data_criacao timestamp default current_timestamp
);

create table reportes_usuario (
    id bigserial primary key,
    denunciante_id bigint not null references usuarios(id) on delete cascade,
    tipo_conteudo tipo_conteudo_enum not null,
    conteudo_id bigint not null,
    motivo_reporte varchar(150) not null,
    descricao_adicional text,
    data_reporte timestamp default current_timestamp
);

create table itens_salvos (
    id bigserial primary key,
    usuario_id bigint not null references usuarios(id) on delete cascade,
    tipo_alvo tipo_alvo_salvo_enum not null,
    alvo_id bigint not null,
    data_salvamento timestamp default current_timestamp,
    constraint salvo_unico unique (usuario_id, tipo_alvo, alvo_id)
);

create index if not exists idx_denuncias_plagio_denunciante on denuncias_plagio(denunciante_id);
create index if not exists idx_denuncias_plagio_denunciado on denuncias_plagio(perfil_denunciado_id);
create index if not exists idx_moderacao_conteudo_autor on moderacao_conteudo(autor_id);
create index if not exists idx_reportes_usuario_denunciante on reportes_usuario(denunciante_id);
create index if not exists idx_itens_salvos_usuario on itens_salvos(usuario_id);

-- Fonte: database/02_tables/10_agenda_gamificacao_e_logs.sql
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
    exibir_publico boolean default false

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
