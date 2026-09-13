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
@Table(name = "funcoes")
@Getter @Setter @NoArgsConstructor
public class Funcao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private AreaArtistica area;
    @Column(nullable = false, length = 100)
    private String nome;
    @ManyToMany
    @JoinTable(name = "funcao_especializacao", joinColumns = @JoinColumn(name = "funcao_id"),
            inverseJoinColumns = @JoinColumn(name = "especializacao_id"))
    private Set<Especializacao> especializacoes = new HashSet<>();
}
