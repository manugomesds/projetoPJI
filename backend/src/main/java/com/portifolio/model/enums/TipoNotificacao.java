package com.portifolio.model.enums;

import java.util.Arrays;

public enum TipoNotificacao implements DatabaseEnum {
    CANDIDATURA("CANDIDATURA"),
    MENSAGEM("MENSAGEM"),
    CONVITE("CONVITE"),
    EDITAL("EDITAL"),
    SALVO("SALVO");

    private final String databaseValue;

    TipoNotificacao(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @Override
    public String getDatabaseValue() {
        return databaseValue;
    }

    public static TipoNotificacao fromDatabaseValue(String value) {
        return Arrays.stream(values())
                .filter(tipo -> tipo.databaseValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tipo de notificacao desconhecido no banco: " + value));
    }
}
