create or replace procedure sp_cadastrar_usuario(
    inout p_usuario_id bigint default null,
    p_nome varchar default null,
    p_data_nascimento date default null,
    p_telefone varchar default null,
    p_email varchar default null,
    p_senha_hash varchar default null,
    p_tipo_usuario tipo_usuario_enum default null,
    p_cpf varchar default null,
    p_cnpj varchar default null,
    p_foto_perfil varchar default null,
    
    -- Específico ARTISTA
    p_tipo_perfil_artista varchar default null,
    p_area_principal_id smallint default null,
    
    -- Específico CONTRATANTE
    p_tipo_contratante varchar default null,
    p_nome_empresa varchar default null,
    
    -- Responsável Legal (Obrigatório para ARTISTA entre 14 e 17 anos)
    p_nome_resp varchar default null,
    p_tel_resp varchar default null,
    p_email_resp varchar default null
)
language plpgsql as $$
declare
    v_tipo_text text;
    v_idade integer;
begin
    -- 1. Tratamento de case-sensitivity do tipo de usuário
    v_tipo_text := upper(p_tipo_usuario::text);

    if v_tipo_text is null or v_tipo_text not in ('ARTISTA', 'CONTRATANTE') then
        raise exception 'Tipo de usuário inválido. Deve ser ARTISTA ou CONTRATANTE.';
    end if;

    -- 2. Cálculo e validação de idade (RF01)
    v_idade := extract(year from age(current_date, p_data_nascimento));

    if v_tipo_text = 'CONTRATANTE' and v_idade < 18 then
        raise exception 'Cadastro negado: CONTRATANTE deve ter no mínimo 18 anos de idade (Idade informada: %).', v_idade;
    elsif v_tipo_text = 'ARTISTA' and v_idade < 14 then
        raise exception 'Cadastro negado: ARTISTA deve ter no mínimo 14 anos de idade (Idade informada: %).', v_idade;
    end if;

    -- 3. Validação de Unicidade (E-mail, CPF e CNPJ)
    if exists (select 1 from usuarios where lower(email) = lower(p_email)) then
        raise exception 'O e-mail informado (%) já está cadastrado no sistema.', p_email;
    end if;

    if p_cpf is not null and exists (select 1 from usuarios where cpf = p_cpf) then
        raise exception 'O CPF informado já está vinculado a outra conta.';
    end if;

    if p_cnpj is not null and exists (select 1 from usuarios where cnpj = p_cnpj) then
        raise exception 'O CNPJ informado já está vinculado a outra conta.';
    end if;

    -- 4. Validação de Campos Específicos por Subtipo (RF01)
    if v_tipo_text = 'ARTISTA' then
        if p_cpf is null or trim(p_cpf) = '' then
            raise exception 'CPF é obrigatório para cadastros do tipo ARTISTA.';
        end if;

        if p_tipo_perfil_artista is null or trim(p_tipo_perfil_artista) = '' then
            raise exception 'Subtipo de perfil artístico é obrigatório.';
        end if;

        -- CNPJ obrigatório para Estúdio e Produtora/Empresa Artística
        if upper(p_tipo_perfil_artista) in ('ESTUDIO', 'PRODUTORA_EMPRESA', 'PRODUTORA/EMPRESA ARTÍSTICA', 'ESTÚDIO') then
            if p_cnpj is null or trim(p_cnpj) = '' then
                raise exception 'CNPJ é obrigatório para perfis do tipo Estúdio ou Produtora/Empresa Artística.';
            end if;
        end if;

        -- Validação da Área Artística Principal
        if p_area_principal_id is null or not exists (select 1 from areas_artisticas where id = p_area_principal_id) then
            raise exception 'Uma área artística principal válida deve ser selecionada.';
        end if;

        -- Validação do Responsável Legal (Exclusivamente ARTISTA entre 14 e 17 anos)
        if v_idade between 14 and 17 then
            if p_nome_resp is null or trim(p_nome_resp) = '' or
               p_tel_resp is null or trim(p_tel_resp) = '' or
               p_email_resp is null or trim(p_email_resp) = '' then
                raise exception 'Artistas entre 14 e 17 anos devem informar nome, telefone e e-mail do responsável legal.';
            end if;
        end if;

    elsif v_tipo_text = 'CONTRATANTE' then
        if p_tipo_contratante is null or trim(p_tipo_contratante) = '' then
            raise exception 'Subtipo de contratante é obrigatório.';
        end if;

        if upper(p_tipo_contratante) in ('PESSOA_FISICA', 'PESSOA FÍSICA') then
            if p_cpf is null or trim(p_cpf) = '' then
                raise exception 'CPF é obrigatório para Contratante Pessoa Física.';
            end if;
        else
            -- PJ / Setor Público / Setor Privado / ONG
            if p_cnpj is null or trim(p_cnpj) = '' then
                raise exception 'CNPJ é obrigatório para Contratantes Pessoa Jurídica / Entidades.';
            end if;
            if p_nome_empresa is null or trim(p_nome_empresa) = '' then
                raise exception 'Nome da empresa/entidade é obrigatório para Contratantes Pessoa Jurídica / Entidades.';
            end if;
        end if;
    end if;

    -- 5. Inserção Transacional do Usuário (Status Inicial: PENDENTE_VERIFICACAO_EMAIL)
    insert into usuarios (
        nome, data_nascimento, telefone, email, senha_hash, tipo_usuario,
        cpf, cnpj, foto_perfil, status_conta, perfil_completo
    ) values (
        p_nome, p_data_nascimento, p_telefone, lower(p_email), p_senha_hash, v_tipo_text::tipo_usuario_enum,
        p_cpf, p_cnpj, p_foto_perfil, 'PENDENTE_VERIFICACAO_EMAIL', false
    ) returning id into p_usuario_id;

    -- 6. Inserção dos Perfis e Relacionamentos
    if v_tipo_text = 'ARTISTA' then
        insert into perfis_artistas (
            usuario_id, tipo_perfil_artista
        ) values (
            p_usuario_id, p_tipo_perfil_artista
        );

        -- Associa a Área Principal na taxonomia relacional
        insert into perfil_artista_area (
            perfil_artista_id, area_id, principal
        ) values (
            p_usuario_id, p_area_principal_id, true
        );

        -- Associa o Responsável Legal se o artista for menor de idade (14-17)
        if v_idade between 14 and 17 then
            insert into responsaveis_legais (
                usuario_id, nome_responsavel, telefone_responsavel, email_responsavel
            ) values (
                p_usuario_id, p_nome_resp, p_tel_resp, p_email_resp
            );
        end if;

    elsif v_tipo_text = 'CONTRATANTE' then
        insert into perfis_contratantes (
            usuario_id, tipo_contratante, nome_empresa
        ) values (
            p_usuario_id, p_tipo_contratante, p_nome_empresa
        );
    end if;

    -- Reavalia o status inicial de completude do perfil
    perform fn_verificar_perfil_completo(p_usuario_id);
end;
$$;