package com.portifolio.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "embeds_externos") @Getter @Setter
public class EmbedExterno {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artista_id", nullable = false) private PerfilArtista artista;
    @Column(name = "url_original", nullable = false, length = 255) private String urlOriginal;
    @Column(name = "codigo_iframe", nullable = false, columnDefinition = "text") private String codigoIframe;
    @Column(name = "tipo_midia", nullable = false, columnDefinition = "tipo_midia_enum") private String tipoMidia = "VIDEO";
    @Column(length = 255) private String legenda;
    @Column(name = "ordem_exibicao") private Integer ordemExibicao = 0;
}
