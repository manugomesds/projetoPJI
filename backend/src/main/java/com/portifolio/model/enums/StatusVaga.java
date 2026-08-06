package com.portifolio.model.enums;

import java.util.Arrays;

public enum StatusVaga implements DatabaseEnum {
    ABERTA("aberta"),
    PAUSADA("pausada"),
    ENCERRADA("encerrada"),
    CANCELADA("cancelada");

    private final String databaseValue;

    StatusVaga(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @Override
    public String getDatabaseValue() {
        return databaseValue;
    }

    public static StatusVaga fromDatabaseValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.databaseValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Status de vaga inválido: " + value));
    }
}
