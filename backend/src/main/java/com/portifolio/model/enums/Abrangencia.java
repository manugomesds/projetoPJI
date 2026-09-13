package com.portifolio.model.enums;

public enum Abrangencia implements DatabaseEnum {
    LOCAL,
    REGIONAL,
    NACIONAL,
    INTERNACIONAL,
    REMOTO;

    @Override
    public String getDatabaseValue() { return name(); }

    public static Abrangencia fromDatabaseValue(String value) { return valueOf(value); }
}
