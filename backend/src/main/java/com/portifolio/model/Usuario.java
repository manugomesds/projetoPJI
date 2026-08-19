package com.portifolio.model;

import com.portifolio.model.enums.TipoUsuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
    private String senha;

    @Column(name = "tipo_usuario", nullable = false, columnDefinition = "tipo_usuario_enum")
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
