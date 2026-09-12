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