package com.portifolio.model.converter;

import com.portifolio.model.enums.TipoAlvoSalvo;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TipoAlvoSalvoConverter implements AttributeConverter<TipoAlvoSalvo, String> {
    public String convertToDatabaseColumn(TipoAlvoSalvo tipo) { return tipo == null ? null : tipo.name(); }
    public TipoAlvoSalvo convertToEntityAttribute(String tipo) { return tipo == null ? null : TipoAlvoSalvo.valueOf(tipo); }
}
