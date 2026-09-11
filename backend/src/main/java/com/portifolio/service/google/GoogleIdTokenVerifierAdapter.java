package com.portifolio.service.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.portifolio.config.GoogleAuthConfig;
import com.portifolio.exception.UnauthorizedException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GoogleIdTokenVerifierAdapter implements GoogleTokenVerifier {

    private static final Pattern EMAIL_VALIDO =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final GoogleIdTokenVerifier verifier;
    private final Clock clock;
    private final String googleClientId;

    public GoogleIdTokenVerifierAdapter(
            GoogleIdTokenVerifier verifier,
            Clock clock,
            @Value("${google.client-id}") String googleClientId) {
        this.verifier = verifier;
        this.clock = clock;
        this.googleClientId = googleClientId;
    }

    @Override
    public GoogleTokenClaims verificar(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw rejeitar();
        }
        try {
            GoogleIdToken idToken = verifier.verify(rawToken);
            if (idToken == null || !claimsValidos(idToken.getPayload())) {
                throw rejeitar();
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            return new GoogleTokenClaims(
                    payload.getSubject(),
                    payload.getEmail().trim().toLowerCase(Locale.ROOT),
                    valorTextual(payload.get("name")),
                    valorTextual(payload.get("picture")));
        } catch (GeneralSecurityException | IOException | RuntimeException ex) {
            if (!(ex instanceof UnauthorizedException)) {
                log.warn("Falha técnica sanitizada ao verificar credencial Google: {}.",
                        ex.getClass().getSimpleName());
            }
            throw ex instanceof UnauthorizedException unauthorized
                    ? unauthorized
                    : rejeitar();
        }
    }

    private boolean claimsValidos(GoogleIdToken.Payload payload) {
        if (payload == null
                || textoAusente(payload.getSubject())
                || payload.getSubject().length() > 255
                || textoAusente(payload.getEmail())
                || payload.getEmail().length() > 150
                || !EMAIL_VALIDO.matcher(payload.getEmail()).matches()
                || !Boolean.TRUE.equals(payload.getEmailVerified())
                || !GoogleAuthConfig.ISSUERS_OFICIAIS.contains(payload.getIssuer())
                || !audienceValida(payload.getAudience())) {
            return false;
        }
        Long expiracao = payload.getExpirationTimeSeconds();
        return expiracao != null && Instant.now(clock).getEpochSecond() < expiracao;
    }

    private boolean audienceValida(Object audience) {
        if (audience instanceof String valor) {
            return googleClientId.equals(valor);
        }
        return audience instanceof Collection<?> valores && valores.contains(googleClientId);
    }

    private String valorTextual(Object valor) {
        return valor instanceof String texto ? texto : null;
    }

    private boolean textoAusente(String valor) {
        return valor == null || valor.isBlank();
    }

    private UnauthorizedException rejeitar() {
        return new UnauthorizedException(MENSAGEM_ERRO);
    }
}
