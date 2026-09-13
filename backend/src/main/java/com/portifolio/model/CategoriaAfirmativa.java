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
@Table(name = "categorias_afirmativas")
@Getter @Setter @NoArgsConstructor
public class CategoriaAfirmativa {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, unique = true, length = 100)
    private String nome;
}
