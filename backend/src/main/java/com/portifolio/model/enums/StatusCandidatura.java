package com.portifolio.model.enums;

import java.util.Arrays;

public enum StatusCandidatura implements DatabaseEnum {
    PENDENTE("pendente"),
    EM_ANALISE("em analise"),
    APROVADO("aprovado"),
    REJEITADO("rejeitado"),
    RETIRADA("retirada"),
    CANCELADA_POR_VAGA("cancelada_por_vaga");

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
