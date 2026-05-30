package com.portifolio.model.converter;

import com.portifolio.model.enums.ModeloTrabalho;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ModeloTrabalhoConverter implements AttributeConverter<ModeloTrabalho, String> {
    @Override
    public String convertToDatabaseColumn(ModeloTrabalho attribute) {
        return attribute == null ? null : attribute.getDatabaseValue();
    }

    @Override
    public ModeloTrabalho convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ModeloTrabalho.fromDatabaseValue(dbData);
    }
}
