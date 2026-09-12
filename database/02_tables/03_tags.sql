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
