-- =============================================================
-- 5. Triggers de recálculo de perfil_completo — versão limpa
--    (deduplicada) do que você colou, mais um gatilho novo que
--    faltava: quando CPF/CNPJ mudam em `usuarios`
-- =============================================================
 
create or replace function fn_trigger_verificar_perfil_usuario()
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
 
drop trigger if exists trg_atualiza_status_perfil_artista on perfis_artistas;
create trigger trg_atualiza_status_perfil_artista
after insert or update or delete on perfis_artistas
for each row execute function fn_trigger_verificar_perfil_usuario();
 
drop trigger if exists trg_atualiza_status_perfil_contratante on perfis_contratantes;
create trigger trg_atualiza_status_perfil_contratante
after insert or update or delete on perfis_contratantes
for each row execute function fn_trigger_verificar_perfil_usuario();
 
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
 
drop trigger if exists trg_atualiza_status_autodeclaracoes on autodeclaracoes;
create trigger trg_atualiza_status_autodeclaracoes
after insert or update or delete on autodeclaracoes
for each row execute function fn_trigger_verificar_perfil_usuario();
 
-- NOVO: faltava recalcular quando CPF/CNPJ mudam em `usuarios`.
-- Repare que a lista "of cpf, cnpj" é proposital — não inclui
-- perfil_completo, senão o UPDATE feito dentro de
-- fn_verificar_perfil_completo dispararia este trigger de novo
-- indefinidamente (recursão infinita).
create or replace function fn_trigger_verificar_perfil_documento()
returns trigger as $$
begin
    perform fn_verificar_perfil_completo(new.id);
    return new;
end;
$$ language plpgsql;
 
drop trigger if exists trg_atualiza_status_perfil_documento on usuarios;
create trigger trg_atualiza_status_perfil_documento
after update of cpf, cnpj on usuarios
for each row execute function fn_trigger_verificar_perfil_documento();
 
