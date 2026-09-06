create table tags (
    id bigserial primary key,
    nome varchar(50) unique not null
);

create table tags_artista (
    artista_id bigint references perfis_artistas(usuario_id) on delete cascade,
    tag_id bigint references tags(id) on delete cascade,
    primary key (artista_id, tag_id)
);
create index if not exists idx_tags_artista_tag on tags_artista(tag_id);
