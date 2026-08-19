package com.portifolio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portifolio.model.enums.TipoUsuario;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsuarioResponse {
    private Long id;
    private String nome;
    private LocalDate dataNascimento;
    private String telefone;
    private String email;
    private TipoUsuario tipoUsuario;
    private Boolean perfilCompleto;
    private LocalDateTime dataCriacao;
    private String nomeResponsavel;
    private String telefoneResponsavel;
    private String emailResponsavel;
}
