CREATE OR REPLACE PROCEDURE sp_realizar_candidatura(
    p_vaga_id INT,
    p_artista_id INT
)
LANGUAGE plpgsql AS $$
DECLARE
    v_vaga_status status_vaga_enum;
    v_perfil_tipo tipo_usuario_enum;
    v_perfil_completo BOOLEAN;
    v_ja_candidatado INT;
BEGIN
    SELECT status INTO v_vaga_status 
    FROM vagas 
    WHERE id = p_vaga_id 
    FOR UPDATE;

    IF v_vaga_status IS NULL OR v_vaga_status <> 'aberta' THEN
        RAISE EXCEPTION 'A vaga não está disponível para candidaturas.'
            USING ERRCODE = '22000';
    END IF;

    SELECT u.perfil_tipo, pa.perfil_completo 
    INTO v_perfil_tipo, v_perfil_completo
    FROM usuarios u
    JOIN perfis_artistas pa ON u.id = pa.usuario_id
    WHERE u.id = p_artista_id;

    IF v_perfil_tipo <> 'artista' OR v_perfil_completo IS NOT TRUE THEN
        RAISE EXCEPTION 'Apenas artistas com perfil completo podem se candidatar.'
            USING ERRCODE = '22000';
    END IF;

    SELECT COUNT(*) INTO v_ja_candidatado 
    FROM candidaturas 
    WHERE vaga_id = p_vaga_id AND artista_id = p_artista_id;

    IF v_ja_candidatado > 0 THEN
        RAISE EXCEPTION 'Artista já cadastrado nesta vaga.'
            USING ERRCODE = '23505';
    END IF;

    INSERT INTO candidaturas (vaga_id, artista_id, status)
    VALUES (p_vaga_id, p_artista_id, 'pendente');
END;
$$;