package com.portifolio.model.converter;

import com.portifolio.model.enums.Abrangencia;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AbrangenciaConverter implements AttributeConverter<Abrangencia, String> {
    @Override
    public String convertToDatabaseColumn(Abrangencia value) {
        return value == null ? null : value.getDatabaseValue();
    }
    @Override
    public Abrangencia convertToEntityAttribute(String value) {
        return value == null ? null : Abrangencia.fromDatabaseValue(value);
    }
}
