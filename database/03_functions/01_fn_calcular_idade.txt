CREATE OR REPLACE FUNCTION fn_calcular_idade(p_data_nascimento DATE)
RETURNS INT AS $$
DECLARE
    v_idade INT;
BEGIN
    v_idade := EXTRACT(YEAR FROM AGE(CURRENT_DATE, p_data_nascimento));
    
    IF v_idade < 14 THEN
        RAISE EXCEPTION 'Idade mínima não permitida. O usuário deve possuir no mínimo 14 anos.'
            USING ERRCODE = '22000';
    END IF;

    RETURN v_idade;
END;
$$ LANGUAGE plpgsql;