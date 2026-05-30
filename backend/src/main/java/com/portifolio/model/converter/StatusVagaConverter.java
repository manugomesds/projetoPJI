package com.portifolio.model.converter;

import com.portifolio.model.enums.StatusVaga;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StatusVagaConverter implements AttributeConverter<StatusVaga, String> {
    @Override
    public String convertToDatabaseColumn(StatusVaga attribute) {
        return attribute == null ? null : attribute.getDatabaseValue();
    }

    @Override
    public StatusVaga convertToEntityAttribute(String dbData) {
        return dbData == null ? null : StatusVaga.fromDatabaseValue(dbData);
    }
}
