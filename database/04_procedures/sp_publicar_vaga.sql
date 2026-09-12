create or replace procedure sp_publicar_vaga(
    inout p_vaga_id bigint default null,
    p_contratante_id bigint default null,
    p_area_id smallint default null,
    p_titulo varchar default null,
    p_descricao text default null,
    p_requisitos text default null,
    p_forma_remuneracao forma_remuneracao_enum default 'A_COMBINAR',
    p_valor_minimo numeric default null,
    p_valor_maximo numeric default null,
    p_cidade varchar default null,
    p_estado varchar default null,
    p_endereco_completo text default null,
    p_beneficios text default null,
    p_modelo_trabalho modelo_trabalho_enum default 'PRESENCIAL',
    p_tipo_contrato varchar default null,
    p_experiencia varchar default null,
    p_data_limite_candidatura timestamp default null,
    p_abrangencia abrangencia_enum default 'LOCAL',
    p_funcoes_ids bigint[] default null,
    p_especializacoes_ids bigint[] default null,
    p_categorias_afirmativas_ids integer[] default null
)
language plpgsql as $$
declare
    v_tipo_usuario text;
    v_func_id bigint;
    v_esp_id bigint;
    v_cat_id integer;
begin
    -- 1. Validação do tipo de usuário contratante
    select upper(tipo_usuario::text) 
    into v_tipo_usuario 
    from usuarios 
    where id = p_contratante_id;

    if v_tipo_usuario is null or v_tipo_usuario <> 'CONTRATANTE' then
        raise exception 'O usuário (ID: %) não é um contratante válido.', p_contratante_id;
    end if;

    -- 2. Transição de RASCUNHO existente para ABERTA ou criação direta de nova vaga
    if p_vaga_id is not null then
        update vagas
        set area_id = coalesce(p_area_id, area_id),
            titulo = coalesce(p_titulo, titulo),
            descricao = coalesce(p_descricao, descricao),
            requisitos = coalesce(p_requisitos, requisitos),
            forma_remuneracao = coalesce(p_forma_remuneracao, forma_remuneracao),
            valor_minimo = coalesce(p_valor_minimo, valor_minimo),
            valor_maximo = coalesce(p_valor_maximo, valor_maximo),
            cidade = coalesce(p_cidade, cidade),
            estado = coalesce(p_estado, estado),
            endereco_completo = coalesce(p_endereco_completo, endereco_completo),
            beneficios = coalesce(p_beneficios, beneficios),
            modelo_trabalho = coalesce(p_modelo_trabalho, modelo_trabalho),
            tipo_contrato = coalesce(p_tipo_contrato, tipo_contrato),
            experiencia = coalesce(p_experiencia, experiencia),
            data_limite_candidatura = coalesce(p_data_limite_candidatura, data_limite_candidatura),
            abrangencia = coalesce(p_abrangencia, abrangencia),
            status = 'ABERTA'::status_vaga_enum,
            data_publicacao = current_timestamp
        where id = p_vaga_id and contratante_id = p_contratante_id;

        if not found then
            raise exception 'Vaga (ID: %) não encontrada para este contratante.', p_vaga_id;
        end if;
    else
        insert into vagas (
            contratante_id, area_id, titulo, descricao, requisitos,
            forma_remuneracao, valor_minimo, valor_maximo, cidade, estado,
            endereco_completo, beneficios, modelo_trabalho, tipo_contrato,
            experiencia, data_limite_candidatura, abrangencia, status, data_publicacao
        ) values (
            p_contratante_id, p_area_id, p_titulo, p_descricao, p_requisitos,
            p_forma_remuneracao, p_valor_minimo, p_valor_maximo, p_cidade, p_estado,
            p_endereco_completo, p_beneficios, p_modelo_trabalho, p_tipo_contrato,
            p_experiencia, p_data_limite_candidatura, p_abrangencia, 'ABERTA'::status_vaga_enum, current_timestamp
        )
        returning id into p_vaga_id;
    end if;

    -- 3. Atualização das Funções (Taxonomia Relacional)
    if p_funcoes_ids is not null then
        delete from vaga_funcao where vaga_id = p_vaga_id;
        foreach v_func_id in array p_funcoes_ids loop
            insert into vaga_funcao (vaga_id, funcao_id)
            values (p_vaga_id, v_func_id)
            on conflict do nothing;
        end loop;
    end if;

    -- 4. Atualização das Especializações (Taxonomia Relacional)
    if p_especializacoes_ids is not null then
        delete from vaga_especializacao where vaga_id = p_vaga_id;
        foreach v_esp_id in array p_especializacoes_ids loop
            insert into vaga_especializacao (vaga_id, especializacao_id)
            values (p_vaga_id, v_esp_id)
            on conflict do nothing;
        end loop;
    end if;

    -- 5. Atualização das Categorias Afirmativas (Ações Afirmativas)
    if p_categorias_afirmativas_ids is not null then
        delete from vagas_categorias_afirmativas where vaga_id = p_vaga_id;
        foreach v_cat_id in array p_categorias_afirmativas_ids loop
            insert into vagas_categorias_afirmativas (vaga_id, categoria_id)
            values (p_vaga_id, v_cat_id)
            on conflict do nothing;
        end loop;
    end if;
end;
$$;