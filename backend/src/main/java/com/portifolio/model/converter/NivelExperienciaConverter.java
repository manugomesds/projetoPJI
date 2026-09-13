package com.portifolio.model.converter;

import com.portifolio.model.enums.NivelExperiencia;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NivelExperienciaConverter implements AttributeConverter<NivelExperiencia, String> {
    @Override
    public String convertToDatabaseColumn(NivelExperiencia value) {
        return value == null ? null : value.getDatabaseValue();
    }
    @Override
    public NivelExperiencia convertToEntityAttribute(String value) {
        return value == null ? null : NivelExperiencia.fromDatabaseValue(value);
    }
}
