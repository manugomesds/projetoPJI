create or replace function fn_gerar_fila_moderacao()
returns trigger as $$
begin
    insert into moderacao_conteudo (
        tipo_conteudo,
        conteudo_id,
        autor_id,
        status_moderacao,
        score_risco
    ) values (
        'GALERIA', -- Mapeado como conteúdo de portfólio/galeria
        new.id,
        new.artista_id,
        'SOB ANALISE',
        0.00
    );
    return new;
end;
$$ language plpgsql;

create trigger trg_inserir_moderacao_portfolio
after insert on portfolio_arquivos
for each row
execute function fn_gerar_fila_moderacao();