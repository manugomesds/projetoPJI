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
@Table(name = "areas_artisticas")
@Getter @Setter @NoArgsConstructor
public class AreaArtistica {
    @Id
    private Short id;
    @Column(nullable = false, unique = true, length = 50)
    private String nome;
}
