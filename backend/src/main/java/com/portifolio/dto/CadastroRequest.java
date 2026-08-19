package com.portifolio.dto;

import com.portifolio.model.enums.TipoUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CadastroRequest {

    @NotBlank(message = "Nome e obrigatorio")
    @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
    private String nome;

    @NotNull(message = "Data de nascimento e obrigatoria")
    private LocalDate dataNascimento;

    @NotBlank(message = "Telefone e obrigatorio")
    @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
    private String telefone;

    @NotBlank(message = "E-mail e obrigatorio")
    @Email(message = "E-mail invalido")
    @Size(max = 150, message = "E-mail deve ter no máximo 150 caracteres")
    private String email;

    @NotBlank(message = "Senha e obrigatoria")
    @Size(min = 8, max = 72, message = "Senha deve ter entre 8 e 72 caracteres")
    private String senha;

    @NotNull(message = "Tipo de usuario e obrigatorio")
    private TipoUsuario tipoUsuario;

    @Size(max = 100, message = "Tipo de perfil deve ter no máximo 100 caracteres")
    private String tipoPerfilContratante;

    // Responsavel legal — obrigatorio se menor de 18 anos
    @Size(max = 150, message = "Nome do responsável deve ter no máximo 150 caracteres")
    private String nomeResponsavel;

    @Size(max = 20, message = "Telefone do responsável deve ter no máximo 20 caracteres")
    private String telefoneResponsavel;

    @Email(message = "E-mail do responsável inválido")
    @Size(max = 150, message = "E-mail do responsável deve ter no máximo 150 caracteres")
    private String emailResponsavel;
}
