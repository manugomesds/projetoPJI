package com.portifolio.model;

import com.portifolio.model.enums.ModeloTrabalho;
import com.portifolio.model.enums.StatusVaga;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vagas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Vaga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contratante_id", nullable = false)
    private PerfilContratante contratante;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, columnDefinition = "text")
    private String descricao;

    @Column(nullable = false, columnDefinition = "text")
    private String requisitos;

    @Column(name = "remunera_valor", nullable = false, precision = 10, scale = 2)
    private BigDecimal remuneraValor;

    @Column(name = "forma_pagamento", nullable = false, length = 100)
    private String formaPagamento;

    @Column(nullable = false, length = 100)
    private String cidade;

    @Column(nullable = false, length = 2)
    private String estado;

    @Column(name = "endereco_completo", columnDefinition = "text")
    private String enderecoCompleto;

    @Column(columnDefinition = "text")
    private String beneficios;

    @Enumerated(EnumType.STRING)
    @Column(name = "modelo_trabalho", nullable = false)
    private ModeloTrabalho modeloTrabalho;

    @Column(name = "tipo_contrato", nullable = false, length = 100)
    private String tipoContrato;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusVaga status;

    @Column(name = "data_publicacao")
    private LocalDateTime dataPublicacao;

    @ManyToMany
    @JoinTable(
            name = "tags_vaga",
            joinColumns = @JoinColumn(name = "vaga_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();
}
