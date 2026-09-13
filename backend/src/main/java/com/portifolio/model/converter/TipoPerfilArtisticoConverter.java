package com.portifolio.model.converter;

import com.portifolio.model.enums.TipoPerfilArtistico;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TipoPerfilArtisticoConverter implements AttributeConverter<TipoPerfilArtistico, String> {
    @Override
    public String convertToDatabaseColumn(TipoPerfilArtistico value) {
        return value == null ? null : value.getDatabaseValue();
    }
    @Override
    public TipoPerfilArtistico convertToEntityAttribute(String value) {
        return value == null ? null : TipoPerfilArtistico.fromDatabaseValue(value);
    }
}
