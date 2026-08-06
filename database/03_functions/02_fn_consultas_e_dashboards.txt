-- =============================================================================
-- 1. Feed de Vagas e Filtro por Tag (RF03 & RF25)
-- Uso: SELECT * FROM fn_feed_vagas(id_artista, id_tag);
-- =============================================================================
CREATE OR REPLACE FUNCTION fn_feed_vagas(
    p_artista_id INT,
    p_tag_id INT DEFAULT NULL
)
RETURNS TABLE (
    vaga_id INT,
    titulo VARCHAR,
    descricao TEXT,
    faixa_salarial VARCHAR,
    modelo_trabalho modelo_trabalho_enum,
    localizacao VARCHAR,
    vaga_status status_vaga_enum,
    nome_empresa_projeto VARCHAR,
    foto_contratante VARCHAR,
    minha_candidatura_status status_candidatura_enum
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        v.id,
        v.titulo,
        v.descricao,
        v.faixa_salarial,
        v.modelo_trabalho,
        v.localizacao,
        v.status,
        pc.nome_empresa_projeto,
        u.foto_perfil_url,
        c.status
    FROM vagas v
    INNER JOIN perfis_contratantes pc ON v.contratante_id = pc.usuario_id
    INNER JOIN usuarios u ON u.id = pc.usuario_id
    LEFT JOIN candidaturas c ON c.vaga_id = v.id AND c.artista_id = p_artista_id
    WHERE (v.status = 'aberta' OR (v.status = 'cancelada' AND c.id IS NOT NULL))
      AND (p_tag_id IS NULL OR EXISTS (
          SELECT 1 FROM vagas_tags vt WHERE vt.vaga_id = v.id AND vt.tag_id = p_tag_id
      ))
    ORDER BY v.criado_em DESC;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- 2. Lista de Candidatos Inscritos na Vaga (RF05)
-- Uso: SELECT * FROM fn_listar_candidatos_vaga(id_vaga);
-- =============================================================================
CREATE OR REPLACE FUNCTION fn_listar_candidatos_vaga(
    p_vaga_id INT
)
RETURNS TABLE (
    candidatura_id INT,
    status_candidatura status_candidatura_enum,
    data_candidatura TIMESTAMP,
    artista_id INT,
    nome_artista VARCHAR,
    foto_perfil_url VARCHAR,
    biografia TEXT,
    localizacao VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        c.id,
        c.status,
        c.criado_em,
        u.id,
        u.nome,
        u.foto_perfil_url,
        pa.biografia,
        pa.localizacao
    FROM candidaturas c
    INNER JOIN usuarios u ON c.artista_id = u.id
    INNER JOIN perfis_artistas pa ON u.id = pa.usuario_id
    WHERE c.vaga_id = p_vaga_id
    ORDER BY c.criado_em DESC;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- 3. Algoritmo de Sugestão de Talentos por Compatibilidade (RF05)
-- Uso: SELECT * FROM fn_sugerir_talentos(id_vaga);
-- =============================================================================
CREATE OR REPLACE FUNCTION fn_sugerir_talentos(
    p_vaga_id INT
)
RETURNS TABLE (
    artista_id INT,
    nome VARCHAR,
    foto_perfil_url VARCHAR,
    biografia TEXT,
    score_compatibilidade BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        u.id,
        u.nome,
        u.foto_perfil_url,
        pa.biografia,
        COUNT(ta.tag_id) AS score
    FROM perfis_artistas pa
    INNER JOIN usuarios u ON pa.usuario_id = u.id
    INNER JOIN tags_artista ta ON ta.artista_id = pa.usuario_id
    INNER JOIN vagas_tags vt ON vt.tag_id = ta.tag_id
    WHERE vt.vaga_id = p_vaga_id 
      AND pa.perfil_completo = TRUE 
      AND u.status = 'ativa'
    GROUP BY u.id, u.nome, u.foto_perfil_url, pa.biografia
    ORDER BY score DESC, u.nome ASC;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- 4. Dashboard do Artista - Vagas Recomendadas (RF11)
-- Uso: SELECT * FROM fn_dashboard_artista(id_artista);
-- =============================================================================
CREATE OR REPLACE FUNCTION fn_dashboard_artista(
    p_artista_id INT
)
RETURNS TABLE (
    vaga_id INT,
    titulo VARCHAR,
    modelo_trabalho modelo_trabalho_enum,
    localizacao VARCHAR,
    criado_em TIMESTAMP,
    nome_empresa_projeto VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    SELECT DISTINCT 
        v.id,
        v.titulo,
        v.modelo_trabalho,
        v.localizacao,
        v.criado_em,
        pc.nome_empresa_projeto
    FROM vagas v
    INNER JOIN perfis_contratantes pc ON v.contratante_id = pc.usuario_id
    INNER JOIN vagas_tags vt ON vt.vaga_id = v.id
    INNER JOIN tags_artista ta ON ta.tag_id = vt.tag_id
    WHERE ta.artista_id = p_artista_id 
      AND v.status = 'aberta'
    ORDER BY v.criado_em DESC
    LIMIT 10;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- 5. Dashboard do Contratante - Candidatos Recentes (RF11)
-- Uso: SELECT * FROM fn_dashboard_contratante(id_contratante);
-- =============================================================================
CREATE OR REPLACE FUNCTION fn_dashboard_contratante(
    p_contratante_id INT
)
RETURNS TABLE (
    candidatura_id INT,
    vaga_titulo VARCHAR,
    artista_nome VARCHAR,
    foto_perfil_url VARCHAR,
    data_candidatura TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        c.id,
        v.titulo,
        u.nome,
        u.foto_perfil_url,
        c.criado_em
    FROM candidaturas c
    INNER JOIN vagas v ON c.vaga_id = v.id
    INNER JOIN usuarios u ON c.artista_id = u.id
    WHERE v.contratante_id = p_contratante_id
    ORDER BY c.criado_em DESC
    LIMIT 10;
END;
$$ LANGUAGE plpgsql;