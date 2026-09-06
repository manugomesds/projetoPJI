create or replace procedure sp_publicar_vaga(
    p_contratante_id bigint,
    p_titulo varchar,
    p_descricao text,
    p_requisitos text,
    p_remunera_valor numeric,
    p_forma_pagamento varchar,
    p_cidade varchar,
    p_estado varchar,
    p_endereco_completo text,
    p_beneficios text,
    p_modelo_trabalho modelo_trabalho_enum,
    p_tipo_contrato varchar,
    p_categoria varchar,
    p_experiencia varchar,
    p_data_limite date,
    p_abrangencia varchar
)
language plpgsql as $$
begin
    insert into vagas (
        contratante_id, titulo, descricao, requisitos, remunera_valor, 
        forma_pagamento, cidade, estado, endereco_completo, beneficios, 
        modelo_trabalho, tipo_contrato, categoria, experiencia, 
        data_limite_candidatura, abrangencia, status
    ) values (
        p_contratante_id, p_titulo, p_descricao, p_requisitos, p_remunera_valor, 
        p_forma_pagamento, p_cidade, p_estado, p_endereco_completo, p_beneficios, 
        p_modelo_trabalho, p_tipo_contrato, p_categoria, p_experiencia, 
        p_data_limite, p_abrangencia, 'aberta'
    );
end;
$$;