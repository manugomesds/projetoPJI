create or replace procedure sp_enviar_candidatura(
    p_vaga_id bigint,
    p_artista_id bigint,
    p_mensagem text,
    p_link_portfolio varchar
)
language plpgsql as $$
begin
    insert into candidaturas (
        vaga_id, artista_id, mensagem_apresentacao, link_portfolio_candidatura, status
    ) values (
        p_vaga_id, p_artista_id, p_mensagem, p_link_portfolio, 'pendente'
    );
end;
$$;