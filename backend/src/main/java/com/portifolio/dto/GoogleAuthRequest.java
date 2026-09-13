package com.portifolio.dto;

import com.portifolio.model.enums.TipoUsuario;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

// RF32 — Login com Google
@Getter
@Setter
public class GoogleAuthRequest {

    private String idToken;

    // Campos exigidos pelo schema para persistir a conta provisória.
    // Não constituem conclusão cadastral nem permitem acesso normal.
    private TipoUsuario tipoUsuario;
    private LocalDate dataNascimento;

    @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
    private String telefone;

    // RF33: lembrar de mim tambem disponivel no login Google
    private Boolean rememberMe = false;
    private com.portifolio.model.enums.TipoPerfilArtistico tipoPerfilArtistico;
}
