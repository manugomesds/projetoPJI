package com.portifolio.dto;

import com.portifolio.model.enums.TipoUsuario;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

// RF32 — Login com Google
@Getter
@Setter
public class GoogleAuthRequest {

    @NotBlank(message = "ID Token do Google e obrigatorio")
    private String idToken;

    // Campos opcionais — obrigatorios apenas no PRIMEIRO acesso (usuario novo)
    // Se nao enviados e usuario nao existe, o backend retorna status AGUARDANDO_DADOS
    private TipoUsuario tipoUsuario;
    private LocalDate dataNascimento;
    private String telefone;

    // RF33: lembrar de mim tambem disponivel no login Google
    private Boolean rememberMe = false;
}