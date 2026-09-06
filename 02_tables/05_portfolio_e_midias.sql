

create table portfolio_arquivos (
    id bigserial primary key,
    artista_id bigint not null references perfis_artistas(usuario_id) on delete cascade,
    url_arquivo varchar(255) not null,
    nome_original varchar(150) not null,
    tamanho_bytes integer not null,
    tipo_mime varchar(50) not null,
    data_upload timestamp default current_timestamp,
    possui_selo boolean default false,
    hash_sha256 char(64) unique,
    data_selo timestamp
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