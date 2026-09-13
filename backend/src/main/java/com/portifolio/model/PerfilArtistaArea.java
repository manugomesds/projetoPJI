package com.portifolio.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.*;
import java.time.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.portifolio.model.enums.*;

@Entity
@Table(name = "perfil_artista_area")
@Getter @Setter @NoArgsConstructor
public class PerfilArtistaArea {
    @EmbeddedId
    private PerfilArtistaAreaId id = new PerfilArtistaAreaId();
    @JsonIgnore @MapsId("perfilArtistaId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "perfil_artista_id")
    private PerfilArtista perfil;
    @MapsId("areaId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id")
    private AreaArtistica area;
    @Column(nullable = false)
    private boolean principal;
    @Column(name = "nivel_experiencia", columnDefinition = "nivel_experiencia_enum")
    private NivelExperiencia nivelExperiencia;
    @Column(name = "ultima_atualizacao")
    private LocalDateTime ultimaAtualizacao;
    @ManyToMany
    @JoinTable(name = "perfil_artista_funcao", joinColumns = {
        @JoinColumn(name = "perfil_artista_id", referencedColumnName = "perfil_artista_id"),
        @JoinColumn(name = "area_id", referencedColumnName = "area_id")},
        inverseJoinColumns = @JoinColumn(name = "funcao_id"))
    private Set<Funcao> funcoes = new HashSet<>();
    @ManyToMany
    @JoinTable(name = "perfil_artista_especializacao", joinColumns = {
        @JoinColumn(name = "perfil_artista_id", referencedColumnName = "perfil_artista_id"),
        @JoinColumn(name = "area_id", referencedColumnName = "area_id")},
        inverseJoinColumns = @JoinColumn(name = "especializacao_id"))
    private Set<Especializacao> especializacoes = new HashSet<>();
}
