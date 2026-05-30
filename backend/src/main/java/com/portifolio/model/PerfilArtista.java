package com.portifolio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.ManyToMany;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "perfis_artistas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PerfilArtista {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(columnDefinition = "text")
    private String biografia;

    @Column(length = 150)
    private String localizacao;

    @Column(name = "url_portfolio", length = 255)
    private String urlPortfolio;

    @Column(name = "nivel_medalha")
    private Integer nivelMedalha;

    @Column(name = "score_engajamento", precision = 5, scale = 2)
    private BigDecimal scoreEngajamento;

    @Column(name = "banner_url", length = 255)
    private String bannerUrl;

    @Column(name = "ultima_atualizacao")
    private LocalDateTime ultimaAtualizacao;

    @ManyToMany
    @jakarta.persistence.JoinTable(
            name = "tags_artista",
            joinColumns = @jakarta.persistence.JoinColumn(name = "artista_id"),
            inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();
}
