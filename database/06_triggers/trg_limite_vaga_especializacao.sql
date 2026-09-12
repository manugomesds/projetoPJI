-- Validação de limite máximo de 5 funções/especializações por VAGA (RF04)
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

drop trigger if exists trg_limite_vaga_funcao on vaga_funcao;
create trigger trg_limite_vaga_funcao
after insert or update on vaga_funcao
for each row execute function fn_validar_limite_vaga_taxonomia();

drop trigger if exists trg_limite_vaga_especializacao on vaga_especializacao;
create trigger trg_limite_vaga_especializacao
after insert or update on vaga_especializacao
for each row execute function fn_validar_limite_vaga_taxonomia();

-- Validação de limite máximo de 5 funções/especializações por ÁREA DO PERFIL (RF08)
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

drop trigger if exists trg_limite_perfil_funcao on perfil_artista_funcao;
create trigger trg_limite_perfil_funcao
after insert or update on perfil_artista_funcao
for each row execute function fn_validar_limite_perfil_taxonomia();

drop trigger if exists trg_limite_perfil_especializacao on perfil_artista_especializacao;
create trigger trg_limite_perfil_especializacao
after insert or update on perfil_artista_especializacao
for each row execute function fn_validar_limite_perfil_taxonomia();


-- =========================================================================
-- MÓDULO 5: VALIDAÇÃO DE COMPATIBILIDADE HIERÁRQUICA DA TAXONOMIA
-- =========================================================================

-- Compatibilidade de especialização vinculada à vaga (RF04)
create or replace function fn_validar_compatibilidade_vaga_especializacao()
returns trigger as $$
begin
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
for each row execute function fn_validar_compatibilidade_vaga_especializacao();

-- Compatibilidade de especialização vinculada à área do perfil do artista (RF08)
create or replace function fn_validar_compatibilidade_perfil_especializacao()
returns trigger as $$
begin
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
for each row execute function fn_validar_compatibilidade_perfil_especializacao();