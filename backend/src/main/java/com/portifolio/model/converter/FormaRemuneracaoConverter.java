package com.portifolio.model.converter;

import com.portifolio.model.enums.FormaRemuneracao;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class FormaRemuneracaoConverter implements AttributeConverter<FormaRemuneracao, String> {
    @Override
    public String convertToDatabaseColumn(FormaRemuneracao value) {
        return value == null ? null : value.getDatabaseValue();
    }
    @Override
    public FormaRemuneracao convertToEntityAttribute(String value) {
        return value == null ? null : FormaRemuneracao.fromDatabaseValue(value);
    }
}
