create or replace function fn_sugerir_artistas_vaga(
    p_vaga_id bigint,
    p_limit integer default 20,
    p_cursor_funcoes integer default null,
    p_cursor_especializacoes integer default null,
    p_cursor_id bigint default null
)
returns table (
    artista_id bigint,
    nome_artista varchar,
    area_nome varchar,
    funcoes_em_comum integer,
    especializacoes_em_comum integer,
    cidade varchar,
    estado varchar
) as $$
declare
    v_area_id smallint;
begin
    -- 1. recupera a área da vaga
    select area_id into v_area_id from vagas where id = p_vaga_id;

    if v_area_id is null then
        return;
    end if;

    -- 2. busca artistas elegíveis com base na interseção taxonômica
    return query
    select 
        u.id as artista_id,
        u.nome as nome_artista,
        aa.nome as area_nome,
        count(distinct vf.funcao_id)::integer as funcoes_em_comum,
        count(distinct ve.especializacao_id)::integer as especializacoes_em_comum,
        p.cidade,
        p.estado
    from perfis_artistas p
    join usuarios u on p.usuario_id = u.id
    join perfil_artista_area paa on p.usuario_id = paa.perfil_artista_id and paa.area_id = v_area_id
    join areas_artisticas aa on paa.area_id = aa.id
    left join perfil_artista_funcao paf on p.usuario_id = paf.perfil_artista_id and paa.area_id = paf.area_id
    left join vaga_funcao vf on paf.funcao_id = vf.funcao_id and vf.vaga_id = p_vaga_id
    left join perfil_artista_especializacao pae on p.usuario_id = pae.perfil_artista_id and paa.area_id = pae.area_id
    left join vaga_especializacao ve on pae.especializacao_id = ve.especializacao_id and ve.vaga_id = p_vaga_id
    where u.perfil_completo = true
      and u.status_conta = 'ATIVO'
    group by 
        u.id, 
        u.nome, 
        aa.nome, 
        p.cidade, 
        p.estado
    having
        p_cursor_id is null
        or (count(distinct vf.funcao_id)::integer < p_cursor_funcoes)
        or (
            count(distinct vf.funcao_id)::integer = p_cursor_funcoes
            and count(distinct ve.especializacao_id)::integer < p_cursor_especializacoes
        )
        or (
            count(distinct vf.funcao_id)::integer = p_cursor_funcoes
            and count(distinct ve.especializacao_id)::integer = p_cursor_especializacoes
            and u.id > p_cursor_id
        )
    order by 
        funcoes_em_comum desc,
        especializacoes_em_comum desc,
        u.id asc
    limit greatest(1, least(p_limit, 100));
end;
$$ language plpgsql;