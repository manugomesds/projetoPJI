package com.portifolio.dto;

import com.portifolio.model.enums.TipoUsuario;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CadastroResponse {

    private Long id;
    private String nome;
    private String email;
    private TipoUsuario tipoUsuario;
    private Boolean menorDeIdade;
    private String mensagem;
}