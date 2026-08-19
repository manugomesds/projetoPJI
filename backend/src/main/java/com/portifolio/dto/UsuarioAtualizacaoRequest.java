package com.portifolio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(value = {
        "id", "senha", "tipoUsuario", "perfilCompleto", "tokenRecuperacao",
        "tokenExpiracao", "dataCriacao", "googleId", "fotoPerfil"
})
public class UsuarioAtualizacaoRequest {

    @NotBlank(message = "Nome e obrigatorio")
    @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
    private String nome;

    // Mantido apenas para compatibilidade; a data de nascimento é imutável.
    private LocalDate dataNascimento;

    @NotBlank(message = "Telefone e obrigatorio")
    @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
    private String telefone;

    @NotBlank(message = "E-mail e obrigatorio")
    @Email(message = "E-mail invalido")
    @Size(max = 150, message = "E-mail deve ter no máximo 150 caracteres")
    private String email;

    @Size(min = 8, max = 72, message = "Nova senha deve ter entre 8 e 72 caracteres")
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
