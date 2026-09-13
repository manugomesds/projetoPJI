package com.portifolio.model.enums;

import java.util.Arrays;

public enum StatusCandidatura implements DatabaseEnum {
    PENDENTE("PENDENTE"),
    EM_ANALISE("EM_ANALISE"),
    ACEITA("ACEITA"),
    REJEITADA("REJEITADA"),
    RETIRADA("RETIRADA"),
    CANCELADA_POR_VAGA("CANCELADA_POR_VAGA");

    private final String databaseValue;

    StatusCandidatura(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @Override
    public String getDatabaseValue() {
        return databaseValue;
    }

    public static StatusCandidatura fromDatabaseValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.databaseValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Status de candidatura inválido: " + value));
    }
}
