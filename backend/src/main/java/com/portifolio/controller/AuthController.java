package com.portifolio.controller;

import com.portifolio.dto.CadastroRequest;
import com.portifolio.dto.CadastroResponse;
import com.portifolio.dto.GoogleAuthRequest;
import com.portifolio.dto.GoogleAuthResponse;
import com.portifolio.dto.LoginRequest;
import com.portifolio.dto.LoginResponse;
import com.portifolio.dto.RefreshRequest;
import com.portifolio.dto.RefreshResponse;
import com.portifolio.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // RF01 — Cadastro convencional (inalterado)
    @PostMapping("/cadastro")
    public ResponseEntity<CadastroResponse> cadastrar(@Valid @RequestBody CadastroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.cadastrar(request));
    }

    // RF02 + RF33 — Login convencional com suporte a "Lembrar de mim"
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // RF32 — Login com Google
    // Retorna status "AUTENTICADO" (token presente) ou "AGUARDANDO_DADOS" (usuario novo sem dados completos)
    @PostMapping("/google")
    public ResponseEntity<GoogleAuthResponse> loginComGoogle(@Valid @RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(authService.loginComGoogle(request));
    }

    // RF33 — Renovar Access Token usando Refresh Token
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    // RF33 — Logout: invalida o Refresh Token informado
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
