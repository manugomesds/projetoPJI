package com.portifolio.model.enums;

import java.util.Arrays;

public enum TipoUsuario implements DatabaseEnum {
    CONTRATANTE("contratante"),
    ARTISTA("artista");

    private final String databaseValue;

    TipoUsuario(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @Override
    public String getDatabaseValue() {
        return databaseValue;
    }

    public static TipoUsuario fromDatabaseValue(String value) {
        return Arrays.stream(values())
                .filter(tipo -> tipo.databaseValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de usuário inválido: " + value));
    }
}
