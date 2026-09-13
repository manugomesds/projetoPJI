package com.portifolio.model;

import com.portifolio.model.enums.TipoUsuario;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.portifolio.model.enums.StatusConta;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(nullable = false, length = 20)
    private String telefone;

    @Column(nullable = false, length = 150, unique = true)
    private String email;

    // RF32: nullable — usuarios Google nao possuem senha local
    @Column(length = 255)
    @JsonIgnore
    private String senha;

    @Column(name = "tipo_usuario", nullable = false, columnDefinition = "tipo_usuario_enum")
    private TipoUsuario tipoUsuario;

    @Column(name = "perfil_completo")
    private Boolean perfilCompleto;

    @Column(name = "token_recuperacao", length = 255)
    @JsonIgnore
    private String tokenRecuperacao;

    @Column(name = "token_expiracao")
    private LocalDateTime tokenExpiracao;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    @JsonIgnore
    @OneToOne(mappedBy = "usuario", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private ResponsavelLegal responsavelLegal;

    @Column(name = "status_conta", nullable = false, columnDefinition = "status_conta_enum")
    private StatusConta statusConta = StatusConta.PENDENTE_VERIFICACAO_EMAIL;
    @JsonIgnore @Column(length = 14, unique = true)
    private String cpf;
    @JsonIgnore @Column(length = 14, unique = true)
    private String cnpj;
    @Column(name = "versao_termo", length = 50)
    private String versaoTermo;
    @Column(name = "email_verificado")
    private Boolean emailVerificado = false;
    @JsonIgnore @Column(name = "token_verificacao", length = 255)
    private String tokenVerificacao;
    @Column(name = "tentativas_verificacao_email")
    private Integer tentativasVerificacaoEmail = 0;
    @Column(name = "ultimo_reenvio_verificacao")
    private LocalDateTime ultimoReenvioVerificacao;

    // Accessors legados delegam à associação normalizada, sem colunas fictícias.
    @JsonIgnore public String getNomeResponsavel() { return responsavelLegal == null ? null : responsavelLegal.getNomeResponsavel(); }
    @JsonIgnore public String getTelefoneResponsavel() { return responsavelLegal == null ? null : responsavelLegal.getTelefoneResponsavel(); }
    @JsonIgnore public String getEmailResponsavel() { return responsavelLegal == null ? null : responsavelLegal.getEmailResponsavel(); }
    public void setNomeResponsavel(String value) { if (value != null) responsavel().setNomeResponsavel(value); }
    public void setTelefoneResponsavel(String value) { if (value != null) responsavel().setTelefoneResponsavel(value); }
    public void setEmailResponsavel(String value) { if (value != null) responsavel().setEmailResponsavel(value); }
    private ResponsavelLegal responsavel() {
        if (responsavelLegal == null) { responsavelLegal = new ResponsavelLegal(); responsavelLegal.setUsuario(this); }
        return responsavelLegal;
    }

    // RF32: identificador unico da conta Google (sub do ID Token)
    @Column(name = "google_id", length = 255, unique = true)
    @JsonIgnore
    private String googleId;

    // RF34: foto vinda do Google no primeiro acesso (Opcao B)
    // Avatar centralizado na conta pelo schema oficial.
    @Column(name = "foto_perfil_url", length = 255)
    private String fotoPerfil;
}