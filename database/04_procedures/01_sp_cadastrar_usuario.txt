CREATE OR REPLACE PROCEDURE sp_cadastrar_usuario(
    p_nome VARCHAR,
    p_email VARCHAR,
    p_senha_hash VARCHAR,
    p_telefone VARCHAR,
    p_data_nascimento DATE,
    p_perfil_tipo enum_perfil_tipo,
    p_nome_resp VARCHAR DEFAULT NULL,
    p_cpf_resp VARCHAR DEFAULT NULL,
    p_email_resp VARCHAR DEFAULT NULL,
    p_telefone_resp VARCHAR DEFAULT NULL,
    INOUT p_novo_id INT DEFAULT NULL
)
LANGUAGE plpgsql AS $$
DECLARE
    v_idade INT;
    v_status enum_usuario_status;
BEGIN
    v_idade := fn_calcular_idade(p_data_nascimento);

    IF v_idade BETWEEN 14 AND 17 THEN
        IF p_nome_resp IS NULL OR p_cpf_resp IS NULL OR p_email_resp IS NULL OR p_telefone_resp IS NULL THEN
            RAISE EXCEPTION 'Dados do responsável legal são obrigatórios para menores de 18 anos.'
                USING ERRCODE = '22000';
        END IF;
        v_status := 'PENDENTE_CONSENTIMENTO';
    ELSE
        v_status := 'PENDENTE';
    END IF;

    INSERT INTO usuarios (nome, email, senha_hash, telefone, data_nascimento, perfil_tipo, status)
    VALUES (p_nome, p_email, p_senha_hash, p_telefone, p_data_nascimento, p_perfil_tipo, v_status)
    RETURNING id INTO p_novo_id;

    IF p_perfil_tipo = 'ARTISTA' THEN
        INSERT INTO perfis_artistas (usuario_id) VALUES (p_novo_id);
    ELSIF p_perfil_tipo = 'CONTRATANTE' THEN
        INSERT INTO perfis_contratantes (usuario_id) VALUES (p_novo_id);
    END IF;

    IF v_status = 'PENDENTE_CONSENTIMENTO' THEN
        INSERT INTO responsaveis_legais (usuario_id, nome_responsavel, cpf_responsavel, email_responsavel, telefone_responsavel)
        VALUES (p_novo_id, p_nome_resp, p_cpf_resp, p_email_resp, p_telefone_resp);
    END IF;
END;
$$;