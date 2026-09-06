create or replace function fn_trigger_verificar_perfil()
returns trigger as $$
begin
    -- Chama a função que já criamos para recalcular e atualizar o booleano na tabela usuarios
    perform fn_verificar_perfil_completo(new.usuario_id);
    return new;
end;
$$ language plpgsql;

create trigger trg_atualiza_status_perfil_artista
after insert or update on perfis_artistas
for each row
execute function fn_trigger_verificar_perfil();

create trigger trg_atualiza_status_perfil_contratante
after insert or update on perfis_contratantes
for each row
execute function fn_trigger_verificar_perfil();