package com.portifolio.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// RF33 — body do POST /api/auth/refresh e POST /api/auth/logout
@Getter
@Setter
public class RefreshRequest {

    @NotBlank(message = "Refresh token e obrigatorio")
    private String refreshToken;
}