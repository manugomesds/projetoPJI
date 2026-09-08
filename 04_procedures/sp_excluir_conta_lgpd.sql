CREATE OR REPLACE PROCEDURE sp_excluir_conta_lgpd(
    p_usuario_id BIGINT,
    p_motivo TEXT,
    p_comprovante_hash CHAR(64)
)
LANGUAGE plpgsql AS $$
BEGIN
    -- =========================================================================
    -- 1. RETER (Obrigação legal / Comprovante sem PII)
    -- =========================================================================
    INSERT INTO log_exclusoes_lgpd (usuario_id_antigo, motivo_opcional, comprovante_hash)
    VALUES (p_usuario_id, p_motivo, p_comprovante_hash);

    -- =========================================================================
    -- 2. ANONIMIZAR (Reatribuição de referências e preservação de histórico)
    -- =========================================================================
    
    -- Candidaturas: preserva registro histórico reatribuindo para o perfil anônimo
    UPDATE candidaturas 
    SET artista_id = 0 
    WHERE artista_id = p_usuario_id;

    -- Vagas publicadas pelo contratante: mantém com status ENCERRADA
    UPDATE vagas 
    SET status = 'ENCERRADA',
        contratante_id = 0 
    WHERE contratante_id = p_usuario_id;

    -- Mensagens de Chat: remove remetente para exibir "Usuário Removido" na interface
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'mensagens_chat') THEN
        UPDATE mensagens_chat 
        SET remetente_id = NULL 
        WHERE remetente_id = p_usuario_id;
    END IF;

    -- Denúncias de Plágio: anonimiza o denunciante
    UPDATE denuncias_plagio 
    SET denunciante_id = 0 
    WHERE denunciante_id = p_usuario_id;

    -- Log de Vagas Canceladas: preserva registro de auditoria reatribuindo o autor
    UPDATE log_vagas_canceladas 
    SET cancelado_por_id = 0 
    WHERE cancelado_por_id = p_usuario_id;

    -- =========================================================================
    -- 3. DELEÇÃO FÍSICA (Remoção total de dados e arquivos sem retenção)
    -- =========================================================================
    
    -- Tokens de sessão e refresh
    DELETE FROM refresh_tokens WHERE usuario_id = p_usuario_id;

    -- Portfólio e mídias
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'portfolio_arquivos') THEN
        DELETE FROM portfolio_arquivos WHERE artista_id = p_usuario_id;
    END IF;

    -- Itens salvos e interações
    DELETE FROM itens_salvos WHERE usuario_id = p_usuario_id;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'visualizacoes_perfil') THEN
        DELETE FROM visualizacoes_perfil WHERE perfil_id = p_usuario_id OR visitante_id = p_usuario_id;
    END IF;

    -- Agenda, Gamificação e Rankings
    DELETE FROM agenda_artista WHERE artista_id = p_usuario_id;
    DELETE FROM conquistas_desbloqueadas WHERE artista_id = p_usuario_id;
    DELETE FROM historico_medalhas WHERE artista_id = p_usuario_id;
    DELETE FROM ranking_top_da_semana WHERE artista_id = p_usuario_id;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'embeds_externos') THEN
        DELETE FROM embeds_externos WHERE artista_id = p_usuario_id;
    END IF;

    -- Responsáveis legais e vínculos de comunidade
    DELETE FROM responsaveis_legais WHERE usuario_id = p_usuario_id;
    DELETE FROM membros_comunidade WHERE usuario_id = p_usuario_id;

    -- Perfis de domínio
    DELETE FROM perfis_artistas WHERE usuario_id = p_usuario_id;
    DELETE FROM perfis_contratantes WHERE usuario_id = p_usuario_id;

    -- =========================================================================
    -- 4. DELEÇÃO FÍSICA DA CONTA (registro em usuarios)
    -- =========================================================================
    DELETE FROM usuarios WHERE id = p_usuario_id;

END;
$$;