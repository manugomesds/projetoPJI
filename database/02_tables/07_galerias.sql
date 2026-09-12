
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