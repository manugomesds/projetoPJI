package com.portifolio.service;

import com.portifolio.dto.ForgotPasswordRequest;
import com.portifolio.dto.PasswordRecoveryResponse;
import com.portifolio.dto.ResetPasswordRequest;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.Usuario;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.validation.PasswordPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordRecoveryService {

    public static final String MENSAGEM_GENERICA =
            "Se o e-mail estiver cadastrado, você receberá as instruções em breve.";

    private static final String MENSAGEM_TOKEN_INVALIDO =
            "Token de recuperação inválido ou expirado.";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final ObjectProvider<PasswordRecoveryEmailSender> emailSenderProvider;
    private final PasswordPolicy passwordPolicy;

    @Transactional
    public PasswordRecoveryResponse solicitar(ForgotPasswordRequest request) {
        PasswordRecoveryResponse resposta = respostaGenerica();
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail().trim()).orElse(null);
        if (usuario == null) {
            return resposta;
        }

        PasswordRecoveryEmailSender emailSender = emailSenderProvider.getIfAvailable();
        if (emailSender == null) {
            return resposta;
        }

        if (usuario.getSenha() == null && usuario.getGoogleId() != null) {
            tentarOrientarLoginGoogle(emailSender, usuario.getEmail());
            return resposta;
        }
        if (usuario.getSenha() == null || usuario.getSenha().isBlank()) {
            return resposta;
        }

        String token = gerarToken();
        usuario.setTokenRecuperacao(hashToken(token));
        usuario.setTokenExpiracao(LocalDateTime.now().plusHours(1));
        usuarioRepository.saveAndFlush(usuario);

        try {
            emailSender.enviarLinkRedefinicao(usuario.getEmail(), token);
        } catch (RuntimeException ex) {
            usuario.setTokenRecuperacao(null);
            usuario.setTokenExpiracao(null);
            usuarioRepository.save(usuario);
            log.warn("Falha ao enviar e-mail de recuperação: {}", ex.getClass().getSimpleName());
        }
        return resposta;
    }

    @Transactional
    public PasswordRecoveryResponse redefinir(ResetPasswordRequest request) {
        String hash = hashToken(request.getToken().trim());
        Usuario usuario = usuarioRepository.findByTokenRecuperacao(hash)
                .filter(this::tokenEstaValido)
                .orElseThrow(() -> new ResourceNotFoundException(MENSAGEM_TOKEN_INVALIDO));

        passwordPolicy.validateOrThrow(request.getNovaSenha());
        usuario.setSenha(passwordEncoder.encode(request.getNovaSenha()));
        usuario.setTokenRecuperacao(null);
        usuario.setTokenExpiracao(null);
        usuarioRepository.save(usuario);
        refreshTokenService.invalidarTodosDoUsuario(usuario.getId());

        return new PasswordRecoveryResponse("Senha redefinida com sucesso.");
    }

    private boolean tokenEstaValido(Usuario usuario) {
        return usuario.getTokenExpiracao() != null
                && usuario.getTokenExpiracao().isAfter(LocalDateTime.now());
    }

    private void tentarOrientarLoginGoogle(
            PasswordRecoveryEmailSender emailSender, String email) {
        try {
            emailSender.enviarOrientacaoLoginGoogle(email);
        } catch (RuntimeException ex) {
            log.warn("Falha ao enviar orientação de login Google: {}",
                    ex.getClass().getSimpleName());
        }
    }

    private PasswordRecoveryResponse respostaGenerica() {
        return new PasswordRecoveryResponse(MENSAGEM_GENERICA);
    }

    private String gerarToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 não está disponível.", ex);
        }
    }
}
