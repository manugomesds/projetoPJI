create or replace function fn_atualizar_timestamp_generica()
returns trigger as $$
begin
    new.ultima_atualizacao = current_timestamp;
    return new;
end;
$$ language plpgsql;

-- Trigger: Atualiza o timestamp da vaga a cada modificação (RF03/RF07)
drop trigger if exists trg_timestamp_vagas on vagas;
create trigger trg_timestamp_vagas
before update on vagas
for each row
execute function fn_atualizar_timestamp_generica();