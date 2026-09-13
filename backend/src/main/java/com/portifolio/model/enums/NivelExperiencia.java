package com.portifolio.model.enums;

public enum NivelExperiencia implements DatabaseEnum {
    SEM_EXPERIENCIA,
    INICIANTE,
    INTERMEDIARIO,
    EXPERIENTE,
    ESPECIALISTA;

    @Override
    public String getDatabaseValue() { return name(); }

    public static NivelExperiencia fromDatabaseValue(String value) { return valueOf(value); }
}
