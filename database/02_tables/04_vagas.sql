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