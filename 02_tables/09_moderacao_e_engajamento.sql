
create table denuncias_plagio (
    id bigserial primary key,
    denunciante_id bigint references usuarios(id) on delete set null,
    perfil_denunciado_id bigint references usuarios(id) on delete set null,
    tipo_violacao tipo_violacao_enum not null,
    descricao_detalhada text not null,
    url_prova_plagio varchar(255),
    status_denuncia status_denuncia_enum default 'RECEBIDA',
    medidas_adotadas text,
    documento_suporte_url varchar(255),
    contestacao text,
    data_contestacao timestamp,
    data_registro timestamp default current_timestamp,
    ultima_atualizacao timestamp default current_timestamp
);

create table moderacao_conteudo (
    id bigserial primary key,
    tipo_conteudo tipo_conteudo_enum not null,
    conteudo_id bigint not null,
    autor_id bigint references usuarios(id) on delete set null,
    moderador_id bigint references usuarios(id) on delete set null,
    contestacao text,
    data_contestacao timestamp,
    status_moderacao status_moderacao_enum default 'SOB ANALISE',
    score_risco numeric(3,2) default 0.00,
    justificativa_acao text,
    data_analise timestamp,
    data_criacao timestamp default current_timestamp
);

create table reportes_usuario (
    id bigserial primary key,
    denunciante_id bigint references usuarios(id) on delete set null,
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

-- No arquivo de moderação e denúncias
create index if not exists idx_denuncias_plagio_denunciante on denuncias_plagio(denunciante_id);
create index if not exists idx_denuncias_plagio_denunciado on denuncias_plagio(perfil_denunciado_id);
create index if not exists idx_moderacao_conteudo_autor on moderacao_conteudo(autor_id);
create index if not exists idx_reportes_usuario_denunciante on reportes_usuario(denunciante_id);
create index if not exists idx_itens_salvos_usuario on itens_salvos(usuario_id);
