package com.portifolio.model.enums;

public enum FormaRemuneracao implements DatabaseEnum {
    POR_HORA,
    DIARIA,
    POR_EVENTO,
    POR_PROJETO,
    MENSAL,
    A_COMBINAR;

    @Override
    public String getDatabaseValue() { return name(); }

    public static FormaRemuneracao fromDatabaseValue(String value) { return valueOf(value); }
}
