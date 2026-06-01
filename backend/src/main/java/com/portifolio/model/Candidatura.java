package com.portifolio.model;

import com.portifolio.model.enums.StatusCandidatura;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "candidaturas", uniqueConstraints = {
        @UniqueConstraint(name = "candidatura_unica", columnNames = {"vaga_id", "artista_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Candidatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vaga_id", nullable = false)
    private Vaga vaga;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artista_id", nullable = false)
    private PerfilArtista artista;

    @Column(name = "mensagem_apresentacao", nullable = false, columnDefinition = "text")
    private String mensagemApresentacao;

    @Column(name = "link_portfolio_candidatura", nullable = false, length = 255)
    private String linkPortfolioCandidatura;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status")
    private StatusCandidatura status;

    @Column(name = "data_candidatura")
    private LocalDateTime dataCandidatura;
}
