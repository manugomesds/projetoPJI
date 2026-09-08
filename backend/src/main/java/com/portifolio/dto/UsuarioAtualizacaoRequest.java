package com.portifolio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsuarioAtualizacaoRequest {

    @NotBlank(message = "Nome e obrigatorio")
    @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
    private String nome;

    // Compatibilidade com o frontend existente. O service ignora este valor:
    // data de nascimento não é editável no RF08.
    private LocalDate dataNascimento;

    @NotBlank(message = "Telefone e obrigatorio")
    @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
    private String telefone;

    @NotBlank(message = "E-mail e obrigatorio")
    @Email(message = "E-mail invalido")
    @Size(max = 150, message = "E-mail deve ter no máximo 150 caracteres")
    private String email;

    private String novaSenha;

    @Size(max = 72, message = "Senha atual deve ter no máximo 72 caracteres")
    private String senhaAtual;

    @Size(max = 150, message = "Nome do responsável deve ter no máximo 150 caracteres")
    private String nomeResponsavel;

    @Size(max = 20, message = "Telefone do responsável deve ter no máximo 20 caracteres")
    private String telefoneResponsavel;

    @Email(message = "E-mail do responsável inválido")
    @Size(max = 150, message = "E-mail do responsável deve ter no máximo 150 caracteres")
    private String emailResponsavel;
}
