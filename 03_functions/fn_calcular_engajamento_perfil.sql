create or replace function fn_calcular_engajamento_perfil(p_artista_id bigint)
returns numeric as $$
declare
    v_total_visualizacoes bigint;
    v_total_interacoes bigint;
    v_total_arquivos bigint;
    v_score numeric(5,2);
begin
    -- Contabilizar visualizações no perfil
    select count(*) into v_total_visualizacoes
    from visualizacoes_perfil
    where perfil_visitado_id = p_artista_id;

    -- Contabilizar interações (curtidas/comentários) nas obras do artista
    select count(*) into v_total_interacoes
    from interacoes_galeria ig
    join portfolio_arquivos pa on ig.arquivo_id = pa.id
    where pa.artista_id = p_artista_id and (ig.curtiu = true or ig.comentario is not null);

    -- Contabilizar arquivos no portfólio
    select count(*) into v_total_arquivos
    from portfolio_arquivos
    where artista_id = p_artista_id;

    -- Cálculo ponderado do score de engajamento
    v_score := (coalesce(v_total_visualizacoes, 0) * 0.2) + 
               (coalesce(v_total_interacoes, 0) * 0.5) + 
               (coalesce(v_total_arquivos, 0) * 1.0);

    -- Atualiza o score diretamente na tabela do artista
    update perfis_artistas
    set score_engajamento = v_score,
        ultima_atualizacao = current_timestamp
    where usuario_id = p_artista_id;

    return v_score;
end;
$$ language plpgsql;