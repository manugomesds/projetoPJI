create or replace procedure sp_atualizar_perfil(
    p_usuario_id bigint,
    p_nome varchar,
    p_telefone varchar,
    p_foto_perfil varchar,
    p_bio text,
    p_localizacao varchar,
    p_extra varchar -- url_portfolio para artistas ou nome_ecreate or replace procedure sp_atualizar_perfil(
    p_usuario_id bigint,
    
    -- Dados Básicos (usuarios)
    p_nome varchar default null,
    p_telefone varchar default null,
    p_foto_perfil varchar default null,
    
    -- Troca de Senha (RNF19 - Hashing validado na aplicação/serviço)
    p_senha_atual_hash varchar default null,
    p_senha_nova_hash varchar default null,
    
    -- Perfil Genérico
    p_bio text default null,
    p_cidade varchar default null,
    p_estado varchar default null,
    
    -- Específico Artista
    p_url_portfolio varchar default null,
    p_disponivel_viagem boolean default null,
    
    -- Específico Contratante
    p_nome_empresa varchar default null,
    
    -- Taxonomia e Autodeclarações (Arrays)
    p_area_principal_id smallint default null,
    p_nivel_experiencia nivel_experiencia_enum default null,
    p_funcoes_ids bigint[] default null,
    p_especializacoes_ids bigint[] default null,
    p_autodeclaracoes_ids integer[] default null
)
language plpgsql as $$
declare
    v_tipo_text text;
    v_senha_hash_atual varchar;
    v_funcao_id bigint;
    v_esp_id bigint;
    v_auto_id integer;
begin
    -- 1. Obtém tipo de usuário e hash atual da senha
    select upper(tipo_usuario::text), senha_hash 
    into v_tipo_text, v_senha_hash_atual
    from usuarios 
    where id = p_usuario_id;

    if v_tipo_text is null then
        raise exception 'Usuário não encontrado (ID: %)', p_usuario_id;
    end if;

    -- 2. Troca de Senha (Valida Hash Atual se fornecida nova senha)
    if p_senha_nova_hash is not null then
        if p_senha_atual_hash is null or v_senha_hash_atual <> p_senha_atual_hash then
            raise exception 'Senha atual incorreta. A alteração de senha foi cancelada.';
        end if;
        
        update usuarios 
        set senha_hash = p_senha_nova_hash 
        where id = p_usuario_id;
    end if;

    -- 3. Atualiza dados genéricos da tabela usuarios
    update usuarios 
    set nome = coalesce(p_nome, nome),
        telefone = coalesce(p_telefone, telefone),
        foto_perfil = coalesce(p_foto_perfil, foto_perfil)
    where id = p_usuario_id;

    -- 4. Atualização por Perfil (Trata Case-Sensitivity do Enum)
    if v_tipo_text = 'ARTISTA' then
        -- Atualiza perfil do artista
        update perfis_artistas
        set biografia = coalesce(p_bio, biografia),
            cidade = coalesce(p_cidade, cidade),
            estado = coalesce(p_estado, estado),
            url_portfolio = coalesce(p_url_portfolio, url_portfolio),
            disponivel_viagem = coalesce(p_disponivel_viagem, disponivel_viagem),
            ultima_atualizacao = current_timestamp
        where usuario_id = p_usuario_id;

        -- Atualiza Área Principal se fornecida
        if p_area_principal_id is not null then
            -- Remove área principal antiga e ajusta nova
            update perfil_artista_area 
            set principal = false 
            where perfil_artista_id = p_usuario_id;

            insert into perfil_artista_area (perfil_artista_id, area_id, principal, nivel_experiencia)
            values (p_usuario_id, p_area_principal_id, true, p_nivel_experiencia)
            on conflict (perfil_artista_id, area_id) 
            do update set principal = true, 
                          nivel_experiencia = coalesce(p_nivel_experiencia, perfil_artista_area.nivel_experiencia);
        end if;

        -- Atualiza Funções (se array fornecido)
        if p_funcoes_ids is not null and p_area_principal_id is not null then
            delete from perfil_artista_funcao 
            where perfil_artista_id = p_usuario_id and area_id = p_area_principal_id;

            foreach v_funcao_id in array p_funcoes_ids loop
                insert into perfil_artista_funcao (perfil_artista_id, area_id, funcao_id)
                values (p_usuario_id, p_area_principal_id, v_funcao_id)
                on conflict do nothing;
            end loop;
        end if;

        -- Atualiza Especializações (se array fornecido)
        if p_especializacoes_ids is not null and p_area_principal_id is not null then
            delete from perfil_artista_especializacao 
            where perfil_artista_id = p_usuario_id and area_id = p_area_principal_id;

            foreach v_esp_id in array p_especializacoes_ids loop
                insert into perfil_artista_especializacao (perfil_artista_id, area_id, especializacao_id)
                values (p_usuario_id, p_area_principal_id, v_esp_id)
                on conflict do nothing;
            end loop;
        end if;

        -- Atualiza Categorias Afirmativas (Autodeclarações)
        if p_autodeclaracoes_ids is not null then
            delete from artista_categoria_afirmativa where artista_id = p_usuario_id;
            
            foreach v_auto_id in array p_autodeclaracoes_ids loop
                insert into artista_categoria_afirmativa (artista_id, categoria_id)
                values (p_usuario_id, v_auto_id)
                on conflict do nothing;
            end loop;
        end if;

    elsif v_tipo_text = 'CONTRATANTE' then
        -- Atualiza perfil do contratante
        update perfis_contratantes
        set biografia = coalesce(p_bio, biografia),
            cidade = coalesce(p_cidade, cidade),
            estado = coalesce(p_estado, estado),
            nome_empresa = coalesce(p_nome_empresa, nome_empresa)
        where usuario_id = p_usuario_id;
    end if;

    -- 5. Reavalia o status de perfil completo
    perform fn_verificar_perfil_completo(p_usuario_id);
end;
$$;mpresa para contratantes
)
language plpgsql as $$
declare
    v_tipo tipo_usuario_enum;
begin
    update usuarios 
    set nome = coalesce(p_nome, nome),
        telefone = coalesce(p_telefone, telefone),
        foto_perfil = coalesce(p_foto_perfil, foto_perfil)
    where id = p_usuario_id;

    select tipo_usuario into v_tipo from usuarios where id = p_usuario_id;

    if v_tipo = 'ARTISTA' then
        update perfis_artistas
        set biografia = coalesce(p_bio, biografia),
            localizacao = coalesce(p_localizacao, localizacao),
            url_portfolio = coalesce(p_extra, url_portfolio),
            ultima_atualizacao = current_timestamp
        where usuario_id = p_usuario_id;
    elsif v_tipo = 'CONTRATANTE' then
        update perfis_contratantes
        set biografia = coalesce(p_bio, biografia),
            localizacao = coalesce(p_localizacao, localizacao),
            nome_empresa = coalesce(p_extra, nome_empresa)
        where usuario_id = p_usuario_id;
    end if;

    perform fn_verificar_perfil_completo(p_usuario_id);
end;
$$;