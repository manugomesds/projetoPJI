-- 2. sp_atualizar_vaga — edição completa do RF07. NUNCA mexe em
--    `status` (isso é papel do RF23/RF28).
-- =============================================================
 
create or replace procedure sp_atualizar_vaga(
    p_vaga_id bigint,
    p_contratante_id bigint,
    p_area_id smallint default null,
    p_titulo varchar default null,
    p_descricao text default null,
    p_requisitos text default null,
    p_forma_remuneracao forma_remuneracao_enum default null,
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
    v_status status_vaga_enum;
    v_func_id bigint;
    v_esp_id bigint;
    v_cat_id integer;
begin
    select status into v_status
    from vagas
    where id = p_vaga_id and contratante_id = p_contratante_id;
 
    if v_status is null then
        raise exception 'Vaga não encontrada ou contratante sem permissão.';
    end if;
 
    if v_status in ('ENCERRADA', 'CANCELADA') then
        raise exception 'Não é permitido alterar dados de vagas encerradas ou canceladas.';
    end if;
 
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
        abrangencia = coalesce(p_abrangencia, abrangencia)
        -- status propositalmente NUNCA é tocado aqui
    where id = p_vaga_id and contratante_id = p_contratante_id;
 
    if p_funcoes_ids is not null then
        delete from vaga_funcao where vaga_id = p_vaga_id;
        foreach v_func_id in array p_funcoes_ids loop
            insert into vaga_funcao (vaga_id, funcao_id)
            values (p_vaga_id, v_func_id)
            on conflict do nothing;
        end loop;
    end if;
 
    if p_especializacoes_ids is not null then
        delete from vaga_especializacao where vaga_id = p_vaga_id;
        foreach v_esp_id in array p_especializacoes_ids loop
            insert into vaga_especializacao (vaga_id, especializacao_id)
            values (p_vaga_id, v_esp_id)
            on conflict do nothing;
        end loop;
    end if;
 
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
 
-- =============================================================
-- 3. Limpeza automática de taxonomia órfã (RF07/RF08)
-- =============================================================
 
-- 3.1 troca de área na vaga -> funções/especializações antigas somem
create or replace function fn_limpar_taxonomia_ao_trocar_area_vaga()
returns trigger as $$
begin
    if new.area_id is distinct from old.area_id then
        delete from vaga_especializacao where vaga_id = new.id;
        delete from vaga_funcao where vaga_id = new.id;
    end if;
    return new;
end;
$$ language plpgsql;
 
drop trigger if exists trg_limpar_taxonomia_area_vaga on vagas;
create trigger trg_limpar_taxonomia_area_vaga
after update of area_id on vagas
for each row execute function fn_limpar_taxonomia_ao_trocar_area_vaga();
 
-- 3.2 remoção de uma função da vaga -> especializações que dependiam
-- só dela ficam órfãs e precisam ser removidas
create or replace function fn_limpar_especializacoes_orfas_vaga()
returns trigger as $$
begin
    delete from vaga_especializacao ve
    where ve.vaga_id = old.vaga_id
      and not exists (
          select 1 from vaga_funcao vf
          join funcao_especializacao fe on fe.funcao_id = vf.funcao_id
          where vf.vaga_id = ve.vaga_id and fe.especializacao_id = ve.especializacao_id
      );
    return old;
end;
$$ language plpgsql;
 
drop trigger if exists trg_limpar_especializacoes_orfas_vaga on vaga_funcao;
create trigger trg_limpar_especializacoes_orfas_vaga
after delete on vaga_funcao
for each row execute function fn_limpar_especializacoes_orfas_vaga();
 
-- 3.3 mesma regra do lado do perfil do artista (RF08)
create or replace function fn_limpar_especializacoes_orfas_perfil()
returns trigger as $$
begin
    delete from perfil_artista_especializacao pae
    where pae.perfil_artista_id = old.perfil_artista_id
      and pae.area_id = old.area_id
      and not exists (
          select 1 from perfil_artista_funcao paf
          join funcao_especializacao fe on fe.funcao_id = paf.funcao_id
          where paf.perfil_artista_id = pae.perfil_artista_id
            and paf.area_id = pae.area_id
            and fe.especializacao_id = pae.especializacao_id
      );
    return old;
end;
$$ language plpgsql;
 
drop trigger if exists trg_limpar_especializacoes_orfas_perfil on perfil_artista_funcao;
create trigger trg_limpar_especializacoes_orfas_perfil
after delete on perfil_artista_funcao
for each row execute function fn_limpar_especializacoes_orfas_perfil();
 
