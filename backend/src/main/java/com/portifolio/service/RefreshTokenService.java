package com.portifolio.service;

import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.RefreshToken;
import com.portifolio.model.Usuario;
import com.portifolio.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// RF33 — Sessao persistente ("Lembrar de mim")
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    @Value("${jwt.refresh.expiration-days:30}")
    private long expiracaoDias;

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Gera um refresh token para o usuario.
     * O token retornado (UUID raw) eh enviado ao cliente.
     * Apenas o hash SHA-256 e persistido no banco (RNF01 — nada sensivel em texto puro).
     */
    @Transactional
    public String gerarRefreshToken(Usuario usuario) {
        String rawToken = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUsuario(usuario);
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setExpiracao(LocalDateTime.now().plusDays(expiracaoDias));
        refreshToken.setAtivo(true);
        refreshToken.setDataCriacao(LocalDateTime.now());

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    /**
     * Valida o token recebido do cliente.
     * Retorna o Usuario dono do token se valido e nao expirado.
     */
    @Transactional
    public Usuario validarRefreshToken(String rawToken) {
        String hash = hashToken(rawToken);

        RefreshToken rt = refreshTokenRepository
                .findByTokenHashAndAtivoTrue(hash)
                .orElseThrow(() -> new ResourceNotFoundException("Refresh token invalido ou ja utilizado."));

        if (rt.getExpiracao().isBefore(LocalDateTime.now())) {
            rt.setAtivo(false);
            refreshTokenRepository.save(rt);
            throw new ResourceNotFoundException("Refresh token expirado. Faca login novamente.");
        }

        return rt.getUsuario();
    }

    /**
     * Invalida um token especifico (logout de um dispositivo).
     */
    @Transactional
    public void invalidarRefreshToken(String rawToken) {
        String hash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHashAndAtivoTrue(hash)
                .ifPresent(rt -> {
                    rt.setAtivo(false);
                    refreshTokenRepository.save(rt);
                });
    }

    /**
     * Invalida todos os tokens de um usuario (logout global / exclusao de conta).
     */
    @Transactional
    public void invalidarTodosDoUsuario(Long usuarioId) {
        refreshTokenRepository.invalidarTodosPorUsuario(usuarioId);
    }

    // Hash SHA-256 do token raw — RNF01: nada sensivel gravado em texto puro
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro interno ao processar token.", e);
        }
    }
}