package com.portifolio.model;

import jakarta.persistence.*;
import com.portifolio.model.enums.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
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

    @Column(name = "tipo_perfil_artistico", nullable = false, columnDefinition = "tipo_perfil_artistico_enum")
    private TipoPerfilArtistico tipoPerfilArtistico;
    @Column(name = "raio_atuacao", columnDefinition = "abrangencia_enum")
    private Abrangencia raioAtuacao;
    @Column(name = "disponivel_oportunidades")
    private Boolean disponivelOportunidades;
    @Column(name = "nome_integrantes", length = 150)
    private String nomeIntegrantes;

    @Column(name = "banner_url", length = 255)
    private String bannerUrl;

    @Column(name = "ultima_atualizacao")
    private LocalDateTime ultimaAtualizacao;

    @OneToMany(mappedBy = "perfil", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PerfilArtistaArea> areas = new HashSet<>();

    /** Visão agregada; alterações pertencem às associações de cada área. */
    public Set<Funcao> getFuncoes() {
        return areas.stream().flatMap(area -> area.getFuncoes().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @PrePersist
    void aplicarValoresPadraoDoSchema() {
        if (ultimaAtualizacao == null) {
            ultimaAtualizacao = LocalDateTime.now();
        }
    }
}
