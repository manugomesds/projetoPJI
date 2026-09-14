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
