package com.portifolio.dto;

import lombok.Builder;
import lombok.Getter;

// RF33 — resposta do POST /api/auth/refresh
@Getter
@Builder
public class RefreshResponse {
    private String token;
}