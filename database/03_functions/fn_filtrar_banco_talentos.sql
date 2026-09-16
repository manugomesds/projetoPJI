create or replace function fn_filtrar_banco_talentos(
    p_vaga_id bigint default null,               -- RF13: contexto de vaga do contratante
    p_termo varchar default null,
    p_area_id smallint default null,
    p_funcao_id bigint default null,
    p_especializacao_id bigint default null,
    p_cidade varchar default null,
    p_estado varchar default null,
    p_raio_atuacao abrangencia_enum default null,
    p_nivel_experiencia nivel_experiencia_enum default null,
    p_disponivel boolean default null,
    p_tipo_perfil_artistico tipo_perfil_artistico_enum default null,
    p_limit integer default 20,
    p_cursor_qtd_funcoes integer default null,
    p_cursor_qtd_especializacoes integer default null,
    p_cursor_ultima_atualizacao timestamp default null,
    p_cursor_id bigint default null
)
returns table (
    artista_id bigint,
    nome_artista varchar,
    biografia text,
    cidade varchar,
    estado varchar,
    qtd_funcoes_coincidentes integer,
    qtd_especializacoes_coincidentes integer,
    ultima_atualizacao timestamp
) as $$
declare
    v_area_id smallint;
begin
    -- área de referência: da vaga, quando houver contexto (RF13:
    -- "utilizar a área da vaga como condição inicial de compatibilidade"),
    -- senão o filtro explícito de área
    if p_vaga_id is not null then
        select vg.area_id into v_area_id from vagas vg where vg.id = p_vaga_id;
    else
        v_area_id := p_area_id;
    end if;

    return query
    with candidatos as (
        select
            p.usuario_id,
            u.nome,
            p.biografia,
            p.cidade,
            p.estado,
            p.ultima_atualizacao,

            -- RF13: quantidade de funções coincidentes do artista
            -- (considerando TODAS as áreas do artista quando não há
            -- contexto de área/vaga — decisão consolidada do RF13)
            (
                select count(*) from perfil_artista_funcao paf
                where paf.perfil_artista_id = p.usuario_id
                  and (v_area_id is null or paf.area_id = v_area_id)
                  and (
                      (p_vaga_id is not null and exists (
                          select 1 from vaga_funcao vf
                          where vf.vaga_id = p_vaga_id and vf.funcao_id = paf.funcao_id))
                      or (p_vaga_id is null and p_funcao_id is not null and paf.funcao_id = p_funcao_id)
                  )
            )::integer as qtd_funcoes,

            -- RF13: quantidade de especializações coincidentes
            (
                select count(*) from perfil_artista_especializacao pae
                where pae.perfil_artista_id = p.usuario_id
                  and (v_area_id is null or pae.area_id = v_area_id)
                  and (
                      (p_vaga_id is not null and exists (
                          select 1 from vaga_especializacao ve
                          where ve.vaga_id = p_vaga_id and ve.especializacao_id = pae.especializacao_id))
                      or (p_vaga_id is null and p_especializacao_id is not null and pae.especializacao_id = p_especializacao_id)
                  )
            )::integer as qtd_especializacoes

        from perfis_artistas p
        join usuarios u on u.id = p.usuario_id
        where
            -- RF13: "somente artistas ativos e com perfil_completo = true"
            p.perfil_completo = true
            and u.status_conta = 'ATIVA'

            and (p_termo is null or u.nome ilike '%' || p_termo || '%' or p.biografia ilike '%' || p_termo || '%')

            -- compatibilidade de área é condição de elegibilidade, mas
            -- olha TODAS as áreas do artista, não só a principal
            and (v_area_id is null or exists (
                    select 1 from perfil_artista_area paa
                    where paa.perfil_artista_id = p.usuario_id and paa.area_id = v_area_id
                ))

            and (p_cidade is null or p.cidade ilike '%' || p_cidade || '%')
            and (p_estado is null or p.estado = p_estado)
            and (p_raio_atuacao is null or p.raio_atuacao = p_raio_atuacao)
            and (p_disponivel is null or p.disponivel_oportunidades = p_disponivel)
            and (p_tipo_perfil_artistico is null or p.tipo_perfil_artistico = p_tipo_perfil_artistico)

            and (p_nivel_experiencia is null or exists (
                    select 1 from perfil_artista_area paa
                    where paa.perfil_artista_id = p.usuario_id
                      and (v_area_id is null or paa.area_id = v_area_id)
                      and paa.nivel_experiencia = p_nivel_experiencia
                ))

            -- NENHUM filtro de medalha/engajamento aqui — proibido pelo RF13
    )
    select
        c.usuario_id,
        c.nome,
        c.biografia,
        c.cidade,
        c.estado,
        c.qtd_funcoes,
        c.qtd_especializacoes,
        c.ultima_atualizacao
    from candidatos c
    where
        -- paginação por cursor/keyset (RNF17): como a ordenação mistura
        -- DESC (funções, especializações, atualização) com ASC (id),
        -- não dá pra comparar as tuplas direto com "<" — precisa da
        -- cadeia de OR abaixo, que reproduz exatamente essa ordenação
        p_cursor_id is null
        or c.qtd_funcoes < p_cursor_qtd_funcoes
        or (c.qtd_funcoes = p_cursor_qtd_funcoes
            and c.qtd_especializacoes < p_cursor_qtd_especializacoes)
        or (c.qtd_funcoes = p_cursor_qtd_funcoes
            and c.qtd_especializacoes = p_cursor_qtd_especializacoes
            and c.ultima_atualizacao < p_cursor_ultima_atualizacao)
        or (c.qtd_funcoes = p_cursor_qtd_funcoes
            and c.qtd_especializacoes = p_cursor_qtd_especializacoes
            and c.ultima_atualizacao = p_cursor_ultima_atualizacao
            and c.usuario_id > p_cursor_id)
    order by
        c.qtd_funcoes desc,
        c.qtd_especializacoes desc,
        c.ultima_atualizacao desc,
        c.usuario_id asc
    limit greatest(1, least(p_limit, 50)); -- RNF17: máximo 50, não 100
end;
$$ language plpgsql;
