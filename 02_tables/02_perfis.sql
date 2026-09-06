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