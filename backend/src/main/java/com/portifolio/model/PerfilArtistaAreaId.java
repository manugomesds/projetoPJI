package com.portifolio.model;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.*;

@Embeddable @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class PerfilArtistaAreaId implements Serializable {
    @Column(name = "perfil_artista_id")
    private Long perfilArtistaId;
    @Column(name = "area_id")
    private Short areaId;
}
