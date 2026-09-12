-- Trigger para tabelas vinculadas diretamente a usuario_id
create or replace function fn_trigger_verificar_perfil_usuario()
returns trigger as $$
declare
    v_usuario_id bicreate or replace function fn_trigger_verificar_perfil_usuario()
returns trigger as $$
declare
    v_usuario_id bigint;
begin
    v_usuario_id := coalesce(new.usuario_id, old.usuario_id);
    
    if v_usuario_id is not null then
        perform fn_verificar_perfil_completo(v_usuario_id);
    end if;
    
    return coalesce(new, old);
end;
$$ language plpgsql;

-- Função auxiliar para tabelas vinculadas por perfil_artista_id
create or replace function fn_trigger_verificar_perfil_artista()
returns trigger as $$
declare
    v_usuario_id bigint;
begin
    v_usuario_id := coalesce(new.perfil_artista_id, old.perfil_artista_id);
    
    if v_usuario_id is not null then
        perform fn_verificar_perfil_completo(v_usuario_id);
    end if;
    
    return coalesce(new, old);
end;
$$ language plpgsql;

-- Triggers de Recalculo em Perfis Principais
drop trigger if exists trg_atualiza_status_perfil_artista on perfis_artistas;
create trigger trg_atualiza_status_perfil_artista
after insert or update or delete on perfis_artistas
for each row execute function fn_trigger_verificar_perfil_usuario();

drop trigger if exists trg_atualiza_status_perfil_contratante on perfis_contratantes;
create trigger trg_atualiza_status_perfil_contratante
after insert or update or delete on perfis_contratantes
for each row execute function fn_trigger_verificar_perfil_usuario();

-- Triggers de Recalculo na Taxonomia do Artista
drop trigger if exists trg_atualiza_status_perfil_area on perfil_artista_area;
create trigger trg_atualiza_status_perfil_area
after insert or update or delete on perfil_artista_area
for each row execute function fn_trigger_verificar_perfil_artista();

drop trigger if exists trg_atualiza_status_perfil_funcao on perfil_artista_funcao;
create trigger trg_atualiza_status_perfil_funcao
after insert or update or delete on perfil_artista_funcao
for each row execute function fn_trigger_verificar_perfil_artista();

drop trigger if exists trg_atualiza_status_perfil_especializacao on perfil_artista_especializacao;
create trigger trg_atualiza_status_perfil_especializacao
after insert or update or delete on perfil_artista_especializacao
for each row execute function fn_trigger_verificar_perfil_artista();

-- Trigger de Recalculo em Autodeclarações
drop trigger if exists trg_atualiza_status_autodeclaracoes on autodeclaracoes;
create trigger trg_atualiza_status_autodeclaracoes
after insert or update or delete on autodeclaracoes
for each row execute function fn_trigger_verificar_perfil_usuario();gint;
begin
    v_usuario_id := coalesce(new.usuario_id, old.usuario_id);
    
    if v_usuario_id is not null then
        perform fn_verificar_perfil_completo(v_usuario_id);
    end if;
    
    return coalesce(new, old);
end;
$$ language plpgsql;

-- Trigger para tabelas de taxonomia vinculadas a perfil_artista_id
create or replace function fn_trigger_verificar_perfil_artista()
returns trigger as $$
declare
    v_usuario_id bigint;
begin
    v_usuario_id := coalesce(new.perfil_artista_id, old.perfil_artista_id);
    
    if v_usuario_id is not null then
        perform fn_verificar_perfil_completo(v_usuario_id);
    end if;
    
    return coalesce(new, old);
end;
$$ language plpgsql;
-- 2.1. Perfis Principais (Artistas e Contratantes)
drop trigger if exists trg_atualiza_status_perfil_artista on perfis_artistas;
create trigger trg_atualiza_status_perfil_artista
after insert or update or delete on perfis_artistas
for each row execute function fn_trigger_verificar_perfil_usuario();

drop trigger if exists trg_atualiza_status_perfil_contratante on perfis_contratantes;
create trigger trg_atualiza_status_perfil_contratante
after insert or update or delete on perfis_contratantes
for each row execute function fn_trigger_verificar_perfil_usuario();

-- 2.2. Taxonomias de Artista (Áreas, Funções e Especializações)
drop trigger if exists trg_atualiza_status_perfil_area on perfil_artista_area;
create trigger trg_atualiza_status_perfil_area
after insert or update or delete on perfil_artista_area
for each row execute function fn_trigger_verificar_perfil_artista();

drop trigger if exists trg_atualiza_status_perfil_funcao on perfil_artista_funcao;
create trigger trg_atualiza_status_perfil_funcao
after insert or update or delete on perfil_artista_funcao
for each row execute function fn_trigger_verificar_perfil_artista();

drop trigger if exists trg_atualiza_status_perfil_especializacao on perfil_artista_especializacao;
create trigger trg_atualiza_status_perfil_especializacao
after insert or update or delete on perfil_artista_especializacao
for each row execute function fn_trigger_verificar_perfil_artista();

-- 2.3. Autodeclarações e Informações Complementares
drop trigger if exists trg_atualiza_status_autodeclaracoes on autodeclaracoes;
create trigger trg_atualiza_status_autodeclaracoes
after insert or update or delete on autodeclaracoes
for each row execute function fn_trigger_verificar_perfil_usuario();

-- =========================================================================
-- 1. Função de Validação de Limites para Vagas (Máximo 5 - RF04)
-- =========================================================================
create or replace function fn_validar_limite_vaga_taxonomia()
returns trigger as $$
declare
    v_total integer;
begin
    if TG_TABLE_NAME = 'vaga_funcao' then
        select count(*) into v_total
        from vaga_funcao
        where vaga_id = new.vaga_id;

        if v_total > 5 then
            raise exception 'Limite excedido: uma vaga pode ter no máximo 5 funções vinculadas (RF04).'
                using errcode = '22000';
        end if;

    elsif TG_TABLE_NAME = 'vaga_especializacao' then
        select count(*) into v_total
        from vaga_especializacao
        where vaga_id = new.vaga_id;

        if v_total > 5 then
            raise exception 'Limite excedido: uma vaga pode ter no máximo 5 especializações vinculadas (RF04).'
                using errcode = '22000';
        end if;
    end if;

    return new;
end;
$$ language plpgsql;

-- Triggers para Vagas
drop trigger if exists trg_limite_vaga_funcao on vaga_funcao;
create trigger trg_limite_vaga_funcao
after insert or update on vaga_funcao
for each row
execute function fn_validar_limite_vaga_taxonomia();

drop trigger if exists trg_limite_vaga_especializacao on vaga_especializacao;
create trigger trg_limite_vaga_especializacao
after insert or update on vaga_especializacao
for each row
execute function fn_validar_limite_vaga_taxonomia();

-- =========================================================================
-- 2. Função de Validação de Limites para Perfis por Área (Máximo 5 - RF08)
-- =========================================================================
create or replace function fn_validar_limite_perfil_taxonomia()
returns trigger as $$
declare
    v_total integer;
begin
    if TG_TABLE_NAME = 'perfil_artista_funcao' then
        select count(*) into v_total
        from perfil_artista_funcao
        where perfil_artista_id = new.perfil_artista_id
          and area_id = new.area_id;

        if v_total > 5 then
            raise exception 'Limite excedido: o perfil não pode ter mais de 5 funções na mesma área (RF08).'
                using errcode = '22000';
        end if;

    elsif TG_TABLE_NAME = 'perfil_artista_especializacao' then
        select count(*) into v_total
        from perfil_artista_especializacao
        where perfil_artista_id = new.perfil_artista_id
          and area_id = new.area_id;

        if v_total > 5 then
            raise exception 'Limite excedido: o perfil não pode ter mais de 5 especializações na mesma área (RF08).'
                using errcode = '22000';
        end if;
    end if;

    return new;
end;
$$ language plpgsql;

-- Triggers para Perfis de Artista
drop trigger if exists trg_limite_perfil_funcao on perfil_artista_funcao;
create trigger trg_limite_perfil_funcao
after insert or update on perfil_artista_funcao
for each row
execute function fn_validar_limite_perfil_taxonomia();

drop trigger if exists trg_limite_perfil_especializacao on perfil_artista_especializacao;
create trigger trg_limite_perfil_especializacao
after insert or update on perfil_artista_especializacao
for each row
execute function fn_validar_limite_perfil_taxonomia();

-- =========================================================================
-- 1. Validação de Compatibilidade em Vagas (vaga_especializacao)
-- =========================================================================
create or replace function fn_validar_compatibilidade_vaga_especializacao()
returns trigger as $$
begin
    -- Verifica se a especialização pertence a pelo menos uma das funções vinculadas à vaga
    if not exists (
        select 1 
        from vaga_funcao vf
        join funcao_especializacao fe on fe.funcao_id = vf.funcao_id
        where vf.vaga_id = new.vaga_id
          and fe.especializacao_id = new.especializacao_id
    ) then
        raise exception 'Incompatibilidade de taxonomia: a especialização (ID: %) não é permitida para nenhuma das funções associadas a esta vaga (RF04).'
            using errcode = '22000';
    end if;

    return new;
end;
$$ language plpgsql;

drop trigger if exists trg_validar_compatibilidade_vaga_especializacao on vaga_especializacao;

create trigger trg_validar_compatibilidade_vaga_especializacao
before insert or update on vaga_especializacao
for each row
execute function fn_validar_compatibilidade_vaga_especializacao();


-- =========================================================================
-- 2. Validação de Compatibilidade no Perfil do Artista (perfil_artista_especializacao)
-- =========================================================================
create or replace function fn_validar_compatibilidade_perfil_especializacao()
returns trigger as $$
begin
    -- Verifica se a especialização pertence a pelo menos uma das funções da MESMA área do perfil
    if not exists (
        select 1 
        from perfil_artista_funcao paf
        join funcao_especializacao fe on fe.funcao_id = paf.funcao_id
        where paf.perfil_artista_id = new.perfil_artista_id
          and paf.area_id = new.area_id
          and fe.especializacao_id = new.especializacao_id
    ) then
        raise exception 'Incompatibilidade de taxonomia: a especialização (ID: %) não é permitida para nenhuma das funções selecionadas na área (ID: %) do perfil (RF08).'
            using errcode = '22000';
    end if;

    return new;
end;
$$ language plpgsql;

drop trigger if exists trg_validar_compatibilidade_perfil_especializacao on perfil_artista_especializacao;

create trigger trg_validar_compatibilidade_perfil_especializacao
before insert or update on perfil_artista_especializacao
for each row
execute function fn_validar_compatibilidade_perfil_especializacao();