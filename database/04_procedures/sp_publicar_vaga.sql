-- =============================================================
-- 1. sp_publicar_vaga — SÓ cria vaga nova ou publica um RASCUNHO.
--    Nunca mais usado para editar uma vaga já ABERTA/PAUSADA.
-- =============================================================
 
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
    p_modelo_trabalho modelo_trabalho_enum default null,
    p_tipo_contrato varchar default null,
    p_experiencia varchar default null,
    p_data_limite_candidatura date default null,
    p_abrangencia abrangencia_enum default null,
    p_funcoes_ids bigint[] default null,
    p_especializacoes_ids bigint[] default null,
    p_categorias_afirmativas_ids integer[] default null
)
language plpgsql as $$
declare
    v_tipo_usuario text;
    v_status_atual status_vaga_enum;
    v_func_id bigint;
    v_esp_id bigint;
    v_cat_id integer;
begin
    -- 1. contratante válido
    select upper(tipo_usuario::text) into v_tipo_usuario
    from usuarios where id = p_contratante_id;
 
    if v_tipo_usuario is null or v_tipo_usuario <> 'CONTRATANTE' then
        raise exception 'O usuário (ID: %) não é um contratante válido.', p_contratante_id;
    end if;
 
    -- 2. campos obrigatórios do RF04 (título, descrição, requisitos,
    -- área, cidade, estado) — sem isso não existe publicação válida
    if p_area_id is null then
        raise exception 'A vaga deve ter exatamente uma área artística selecionada.';
    end if;
    if p_titulo is null or trim(p_titulo) = '' then
        raise exception 'O título da vaga é obrigatório.';
    end if;
    if p_descricao is null or trim(p_descricao) = '' then
        raise exception 'A descrição da vaga é obrigatória.';
    end if;
    if p_requisitos is null or trim(p_requisitos) = '' then
        raise exception 'Os requisitos da vaga são obrigatórios.';
    end if;
    if p_cidade is null or p_estado is null then
        raise exception 'Cidade e Estado são obrigatórios.';
    end if;
 
    -- 3. cria vaga nova OU publica um RASCUNHO existente
    if p_vaga_id is not null then
        select status into v_status_atual
        from vagas
        where id = p_vaga_id and contratante_id = p_contratante_id;
 
        if v_status_atual is null then
            raise exception 'Vaga (ID: %) não encontrada para este contratante.', p_vaga_id;
        end if;
 
        -- RF23: a única transição de publicação é RASCUNHO -> ABERTA.
        -- Editar uma vaga já publicada é responsabilidade do
        -- sp_atualizar_vaga (RF07); mudar o estado dela é
        -- responsabilidade dos procedimentos do RF23/RF28.
        if v_status_atual <> 'RASCUNHO' then
            raise exception 'Só é possível publicar vagas em RASCUNHO (estado atual: %). Use sp_atualizar_vaga para editar o conteúdo, ou os procedimentos de suspender/reabrir/encerrar/cancelar para mudar o estado.', v_status_atual;
        end if;
 
        update vagas
        set area_id = p_area_id,
            titulo = p_titulo,
            descricao = p_descricao,
            requisitos = p_requisitos,
            forma_remuneracao = p_forma_remuneracao,
            valor_minimo = p_valor_minimo,
            valor_maximo = p_valor_maximo,
            cidade = p_cidade,
            estado = p_estado,
            endereco_completo = p_endereco_completo,
            beneficios = p_beneficios,
            modelo_trabalho = p_modelo_trabalho,
            tipo_contrato = p_tipo_contrato,
            experiencia = p_experiencia,
            data_limite_candidatura = p_data_limite_candidatura,
            abrangencia = p_abrangencia,
            status = 'ABERTA'::status_vaga_enum,
            data_publicacao = current_timestamp
        where id = p_vaga_id;
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
            p_experiencia, p_data_limite_candidatura, p_abrangencia,
            'ABERTA'::status_vaga_enum, current_timestamp
        )
        returning id into p_vaga_id;
    end if;
