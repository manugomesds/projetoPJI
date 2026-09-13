package com.portifolio.model.converter;

import com.portifolio.model.enums.StatusConta;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StatusContaConverter implements AttributeConverter<StatusConta, String> {
    @Override
    public String convertToDatabaseColumn(StatusConta value) {
        return value == null ? null : value.getDatabaseValue();
    }
    @Override
    public StatusConta convertToEntityAttribute(String value) {
        return value == null ? null : StatusConta.fromDatabaseValue(value);
    }
}
