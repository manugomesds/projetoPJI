package com.portifolio.dto;

import com.portifolio.model.enums.TipoUsuario;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UsuarioResponse {
    private Long id;
    private String nome;
    private LocalDate dataNascimento;
    private String telefone;
    private String email;
    private TipoUsuario tipoUsuario;
    private Boolean perfilCompleto;
    private LocalDateTime dataCriacao;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String nomeResponsavel;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String telefoneResponsavel;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String emailResponsavel;
}
