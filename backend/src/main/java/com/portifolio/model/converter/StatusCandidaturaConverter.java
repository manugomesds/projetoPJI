package com.portifolio.model.converter;

import com.portifolio.model.enums.StatusCandidatura;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StatusCandidaturaConverter implements AttributeConverter<StatusCandidatura, String> {
    @Override
    public String convertToDatabaseColumn(StatusCandidatura attribute) {
        return attribute == null ? null : attribute.getDatabaseValue();
    }

    @Override
    public StatusCandidatura convertToEntityAttribute(String dbData) {
        return dbData == null ? null : StatusCandidatura.fromDatabaseValue(dbData);
    }
}
