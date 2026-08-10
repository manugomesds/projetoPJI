package com.portifolio.model;

import com.portifolio.model.enums.TipoUsuario;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    private String senha;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo_usuario")
    private TipoUsuario tipoUsuario;

    @Column(name = "perfil_completo")
    private Boolean perfilCompleto;

    @Column(name = "token_recuperacao", length = 255)
    private String tokenRecuperacao;

    @Column(name = "token_expiracao")
    private LocalDateTime tokenExpiracao;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    @Column(name = "nome_responsavel", length = 150)
    private String nomeResponsavel;

    @Column(name = "telefone_responsavel", length = 20)
    private String telefoneResponsavel;

    @Column(name = "email_responsavel", length = 150)
    private String emailResponsavel;

    // RF32: identificador unico da conta Google (sub do ID Token)
    @Column(name = "google_id", length = 255, unique = true)
    private String googleId;

    // RF34: foto vinda do Google no primeiro acesso (Opcao B)
    // Sobrescrita quando usuario define foto propria em perfis_artistas/perfis_contratantes
    @Column(name = "foto_perfil", length = 255)
    private String fotoPerfil;
}