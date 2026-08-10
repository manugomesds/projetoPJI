package com.portifolio.dto;

import com.portifolio.model.enums.TipoUsuario;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private String token;
    private Long id;
    private String nome;
    private String email;
    private TipoUsuario tipoUsuario;
    private Boolean perfilCompleto;

    // RF34: URL do avatar (foto real ou DiceBear gerado — nunca null)
    private String avatarUrl;

    // RF33: presente apenas quando rememberMe = true; null caso contrario
    private String refreshToken;
}