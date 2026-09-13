package com.portifolio.model.enums;

public enum TipoPerfilArtistico implements DatabaseEnum {
    ARTISTA_SOLO,
    DUPLA,
    BANDA,
    GRUPO_ARTISTICO,
    ESTUDIO,
    PRODUTORA_EMPRESA;

    @Override
    public String getDatabaseValue() { return name(); }

    public static TipoPerfilArtistico fromDatabaseValue(String value) { return valueOf(value); }
}
