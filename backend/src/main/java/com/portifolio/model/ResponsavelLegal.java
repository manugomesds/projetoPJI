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
@Table(name = "responsaveis_legais")
@Getter @Setter @NoArgsConstructor
public class ResponsavelLegal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", unique = true)
    private Usuario usuario;
    @Column(name = "nome_responsavel", nullable = false, length = 150)
    private String nomeResponsavel;
    @Column(name = "telefone_responsavel", nullable = false, length = 20)
    private String telefoneResponsavel;
    @Column(name = "email_responsavel", nullable = false, length = 150)
    private String emailResponsavel;
    @Column(name = "versao_termo", length = 50)
    private String versaoTermo;
    @JsonIgnore
    @Column(name = "token_consentimento", length = 255)
    private String tokenConsentimento;
    @Column(name = "consentimento_revogado")
    private Boolean consentimentoRevogado = false;
    @Column(name = "data_consentimento")
    private LocalDateTime dataConsentimento;
}
