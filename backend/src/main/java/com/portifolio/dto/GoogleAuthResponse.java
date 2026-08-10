package com.portifolio.dto;

import com.portifolio.model.enums.TipoUsuario;
import lombok.Builder;
import lombok.Getter;

// RF32 — Resposta do login com Google
// status "AUTENTICADO"    → usuario logado, token presente
// status "AGUARDANDO_DADOS" → usuario novo sem dados completos; frontend exibe form de conclusao
@Getter
@Builder
public class GoogleAuthResponse {

    private String status;

    // Preenchidos quando status = AUTENTICADO
    private String token;
    private String refreshToken;
    private Long id;
    private String nome;
    private String email;
    private TipoUsuario tipoUsuario;
    private Boolean perfilCompleto;
    private String avatarUrl;

    // Preenchidos quando status = AGUARDANDO_DADOS (para pre-preencher o form no frontend)
    private String nomeGoogle;
    private String emailGoogle;
    private String fotoGoogle;
}