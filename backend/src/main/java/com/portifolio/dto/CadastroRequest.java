package com.portifolio.dto;

import com.portifolio.model.enums.TipoUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CadastroRequest {

    @NotBlank(message = "Nome e obrigatorio")
    private String nome;

    @NotNull(message = "Data de nascimento e obrigatoria")
    private LocalDate dataNascimento;

    @NotBlank(message = "Telefone e obrigatorio")
    private String telefone;

    @NotBlank(message = "E-mail e obrigatorio")
    @Email(message = "E-mail invalido")
    private String email;

    @NotBlank(message = "Senha e obrigatoria")
    private String senha;

    @NotNull(message = "Tipo de usuario e obrigatorio")
    private TipoUsuario tipoUsuario;

    private String tipoPerfilContratante;

    // Responsavel legal — obrigatorio se menor de 18 anos
    private String nomeResponsavel;
    private String telefoneResponsavel;
    private String emailResponsavel;
}
