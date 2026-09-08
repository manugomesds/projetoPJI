package com.portifolio.dto;

import com.portifolio.model.enums.TipoUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsuarioRequest {

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
    private String nome;

    @NotNull(message = "Data de nascimento é obrigatória")
    private LocalDate dataNascimento;

    @NotBlank(message = "Telefone é obrigatório")
    @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
    private String telefone;

    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    @Size(max = 150, message = "E-mail deve ter no máximo 150 caracteres")
    private String email;

    private String senha;

    @NotNull(message = "Tipo de usuário é obrigatório")
    private TipoUsuario tipoUsuario;

    private Boolean perfilCompleto;
    @Size(max = 255, message = "Token de recuperação deve ter no máximo 255 caracteres")
    private String tokenRecuperacao;
    private LocalDateTime tokenExpiracao;

    @Size(max = 150, message = "Nome do responsável deve ter no máximo 150 caracteres")
    private String nomeResponsavel;

    @Size(max = 20, message = "Telefone do responsável deve ter no máximo 20 caracteres")
    private String telefoneResponsavel;

    @Email(message = "E-mail do responsável inválido")
    @Size(max = 150, message = "E-mail do responsável deve ter no máximo 150 caracteres")
    private String emailResponsavel;
}
