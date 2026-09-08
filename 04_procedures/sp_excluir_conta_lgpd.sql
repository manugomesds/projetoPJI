CREATE OR REPLACE PROCEDURE sp_excluir_conta_lgpd(
    p_usuario_id bigint,
    p_motivo text,
    p_comprovante_hash char(64)
)
LANGUAGE plpgsql AS $$
BEGIN
    -- 1. Armazena o log de exclusão anônimo obrigatório (sua lógica original mantida)
    INSERT INTO log_exclusoes_lgpd (usuario_id_antigo, motivo_opcional, comprovante_hash)
    VALUES (p_usuario_id, p_motivo, p_comprovante_hash);

    -- 2. Anonimiza a tabela principal de usuários em vez de deletar (Evita o CASCADE destrutivo)
    UPDATE usuarios
    SET nome = 'Usuário Excluído',
        email = 'excluido_' || p_comprovante_hash || '@anonimo.com', -- Usa o hash para manter o e-mail único
        telefone = '00000000000',
        senha = NULL,
        google_id = NULL,
        foto_perfil = NULL,
        token_recuperacao = NULL,
        token_expiracao = NULL
    WHERE id = p_usuario_id;

    -- 3. Limpa PII de perfis_artistas (se existir)
    UPDATE perfis_artistas
    SET biografia = 'Perfil excluído a pedido do usuário.',
        localizacao = NULL,
        url_portfolio = NULL,
        banner_url = NULL
    WHERE usuario_id = p_usuario_id;

    -- 4. Limpa PII de perfis_contratantes (se existir)
    UPDATE perfis_contratantes
    SET nome_empresa = 'Empresa Excluída',
        biografia = 'Perfil excluído a pedido do usuário.',
        localizacao = NULL,
        banner_url = NULL
    WHERE usuario_id = p_usuario_id;

    -- 5. Apaga vínculos que realmente não importam para histórico
    DELETE FROM responsaveis_legais WHERE usuario_id = p_usuario_id;
END;
$$;