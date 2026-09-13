package com.portifolio.model.enums;

public enum StatusConta implements DatabaseEnum {
    PENDENTE_VERIFICACAO_EMAIL,
    PENDENTE_TIPO_PERFIL,
    PENDENTE_CONSENTIMENTO,
    ATIVA,
    BLOQUEADA;

    @Override
    public String getDatabaseValue() { return name(); }

    public static StatusConta fromDatabaseValue(String value) { return valueOf(value); }
}
