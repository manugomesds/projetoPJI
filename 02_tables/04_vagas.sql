
create table vagas (
    id bigserial primary key,
    contratante_id bigint references perfis_contratantes(usuario_id) on delete set null,
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
    categoria varchar(100),
    experiencia varchar(100),
    data_limite_candidatura date,
    abrangencia varchar(30),
    status status_vaga_enum default 'ABERTA',
    data_publicacao timestamp default current_timestamp
);

create table tags_vaga (
    vaga_id bigint references vagas(id) on delete cascade,
    tag_id bigint references tags(id) on delete cascade,
    primary key (vaga_id, tag_id)
);

create table fotos_vaga (
    vaga_id bigint not null references vagas(id) on delete cascade,
    ordem integer not null,
    url varchar(500) not null,
    primary key (vaga_id, ordem)
);

create table candidaturas (
    id bigserial primary key,
    vaga_id bigint not null references vagas(id) on delete cascade,
    artista_id bigint references perfis_artistas(usuario_id) on delete set null,
    mensagem_apresentacao text not null,
    link_portfolio_candidatura varchar(255) not null,
    status status_candidatura_enum default 'PENDENTE',
    data_candidatura timestamp default current_timestamp,
    constraint candidatura_unica unique (vaga_id, artista_id)
);

create table log_vagas_canceladas (
    id bigserial primary key,
    vaga_id bigint not null references vagas(id) on delete cascade,
    cancelado_por_id bigint references usuarios(id) on delete set null,
    data_cancelamento timestamp default current_timestamp,
    motivo text
);

create index if not exists idx_tags_vaga_tag on tags_vaga(tag_id);
create index if not exists idx_candidaturas_artista on candidaturas(artista_id);
create index if not exists idx_candidaturas_vaga on candidaturas(vaga_id);
create index if not exists idx_log_vagas_canceladas_vaga on log_vagas_canceladas(vaga_id);
create index if not exists idx_log_vagas_canceladas_usuario on log_vagas_canceladas(cancelado_por_id);
create index if not exists idx_vagas_status_id on vagas(status, id);
create index if not exists idx_vagas_cidade on vagas(cidade);
create index if not exists idx_vagas_estado on vagas(estado);
create index if not exists idx_vagas_contratante_id on vagas(contratante_id);
