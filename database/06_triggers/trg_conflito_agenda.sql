create or replace function fn_verificar_conflito_agenda()
returns trigger as $$
begin
    if exists (
        select 1 
        from agenda_artista
        where artista_id = new.artista_id
          and id <> coalesce(new.id, -1)
          and tsrange(data_hora_inicio, data_hora_fim, '[)') &&
              tsrange(new.data_hora_inicio, new.data_hora_fim, '[)')
    ) then
        raise exception 'Conflito de horário com compromisso existente do artista %', new.artista_id
            using errcode = '22000';
    end if;
    
    return new;
end;
$$ language plpgsql;