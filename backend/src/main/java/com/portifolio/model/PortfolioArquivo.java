package com.portifolio.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "portfolio_arquivos") @Getter @Setter
public class PortfolioArquivo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artista_id", nullable = false) private PerfilArtista artista;
    @Column(name = "url_arquivo", nullable = false, length = 255) private String urlArquivo;
    @Column(name = "nome_original", nullable = false, length = 150) private String nomeOriginal;
    @Column(name = "tamanho_bytes", nullable = false) private Integer tamanhoBytes;
    @Column(name = "tipo_mime", nullable = false, length = 50) private String tipoMime;
    @Column(name = "data_upload") private LocalDateTime dataUpload;
}
