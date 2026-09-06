create or replace function fn_atualizar_timestamp_generica()
returns trigger as $$
begin
    new.ultima_atualizacao = current_timestamp;
    return new;
end;
$$ language plpgsql;

create trigger trg_timestamp_perfis_artistas
before update on perfis_artistas
for each row
execute function fn_atualizar_timestamp_generica();

create trigger trg_timestamp_denuncias
before update on denuncias_plagio
for each row
execute function fn_atualizar_timestamp_generica();