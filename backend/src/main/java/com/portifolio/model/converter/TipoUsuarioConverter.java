package com.portifolio.model.converter;

import com.portifolio.model.enums.TipoUsuario;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TipoUsuarioConverter implements AttributeConverter<TipoUsuario, String> {
    @Override
    public String convertToDatabaseColumn(TipoUsuario attribute) {
        return attribute == null ? null : attribute.getDatabaseValue();
    }

    @Override
    public TipoUsuario convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TipoUsuario.fromDatabaseValue(dbData);
    }
}
