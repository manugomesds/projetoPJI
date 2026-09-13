package com.portifolio.model.enums;

import java.util.Arrays;

public enum ModeloTrabalho implements DatabaseEnum {
    PRESENCIAL("PRESENCIAL"),
    REMOTO("REMOTO"),
    HIBRIDO("HIBRIDO");

    private final String databaseValue;

    ModeloTrabalho(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @Override
    public String getDatabaseValue() {
        return databaseValue;
    }

    public static ModeloTrabalho fromDatabaseValue(String value) {
        return Arrays.stream(values())
                .filter(modelo -> modelo.databaseValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Modelo de trabalho inválido: " + value));
    }
}
