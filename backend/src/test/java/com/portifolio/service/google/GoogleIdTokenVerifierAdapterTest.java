package com.portifolio.service.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.portifolio.config.GoogleAuthConfig;
import com.portifolio.exception.UnauthorizedException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GoogleIdTokenVerifierAdapterTest {

    private static final String CLIENT_ID = "cliente-google-esperado";
    private static final Instant AGORA = Instant.parse("2026-09-11T15:00:00Z");

    private GoogleIdTokenVerifier verifier;
    private GoogleIdToken idToken;
    private GoogleIdTokenVerifierAdapter adapter;

    @BeforeEach
    void configurar() {
        verifier = mock(GoogleIdTokenVerifier.class);
        idToken = mock(GoogleIdToken.class);
        adapter = new GoogleIdTokenVerifierAdapter(
                verifier, Clock.fixed(AGORA, ZoneOffset.UTC), CLIENT_ID);
    }

    @Test
    void tokenValidoAceitaClaimsConfiaveisENormalizaEmail() throws Exception {
        GoogleIdToken.Payload payload = payloadValido().setEmail("PESSOA@EXAMPLE.COM");
        when(verifier.verify("token-valido")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);

        GoogleTokenClaims claims = adapter.verificar("token-valido");

        assertThat(claims).isEqualTo(new GoogleTokenClaims(
                "sub-123", "pessoa@example.com", "Pessoa Google", "https://img.example/avatar"));
    }

    @Test
    void configuracaoOficialFixaAudienceEIssuersGoogle() {
        GoogleIdTokenVerifier configurado = new GoogleAuthConfig().googleIdTokenVerifier(CLIENT_ID);

        assertThat(configurado.getAudience()).containsExactly(CLIENT_ID);
        assertThat(configurado.getIssuers()).containsExactlyElementsOf(GoogleAuthConfig.ISSUERS_OFICIAIS);
    }

    @Test
    void assinaturaOuTokenInvalidoRetornaFalhaGenerica() throws Exception {
        when(verifier.verify("token-invalido")).thenReturn(null);

        assertFalhaGenerica("token-invalido");
    }

    @Test
    void erroTecnicoDoVerifierRetornaFalhaGenerica() throws Exception {
        when(verifier.verify("token-com-erro")).thenThrow(new IOException("audience interna secreta"));

        assertFalhaGenerica("token-com-erro");
    }

    @Test
    void tokenExpiradoRetornaFalhaGenerica() throws Exception {
        configurarPayload(payloadValido().setExpirationTimeSeconds(AGORA.minusSeconds(1).getEpochSecond()));

        assertFalhaGenerica("token");
    }

    @Test
    void audienceIncorretaRetornaFalhaGenerica() throws Exception {
        configurarPayload(payloadValido().setAudience("outro-cliente"));

        assertFalhaGenerica("token");
    }

    @Test
    void issuerIncorretoRetornaFalhaGenerica() throws Exception {
        configurarPayload(payloadValido().setIssuer("https://issuer.example"));

        assertFalhaGenerica("token");
    }

    @Test
    void emailNaoVerificadoRetornaFalhaGenerica() throws Exception {
        configurarPayload(payloadValido().setEmailVerified(false));

        assertFalhaGenerica("token");
    }

    @Test
    void emailVerifiedAusenteRetornaFalhaGenerica() throws Exception {
        configurarPayload(payloadValido().setEmailVerified(null));

        assertFalhaGenerica("token");
    }

    @Test
    void emailAusenteRetornaFalhaGenerica() throws Exception {
        configurarPayload(payloadValido().setEmail(null));

        assertFalhaGenerica("token");
    }

    @Test
    void subjectAusenteRetornaFalhaGenerica() throws Exception {
        configurarPayload(payloadValido().setSubject(null));

        assertFalhaGenerica("token");
    }

    @Test
    void falhaNaoRevelaTokenClaimEmailAudienceOuCausa() throws Exception {
        String tokenSensivel = "token-secreto-123";
        when(verifier.verify(tokenSensivel)).thenThrow(new IOException(
                "email pessoa@example.com audience " + CLIENT_ID));

        assertThatThrownBy(() -> adapter.verificar(tokenSensivel))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(GoogleTokenVerifier.MENSAGEM_ERRO)
                .hasMessageNotContaining(tokenSensivel)
                .hasMessageNotContaining("pessoa@example.com")
                .hasMessageNotContaining(CLIENT_ID)
                .hasMessageNotContaining("audience");
    }

    @Test
    void audienceEmListaComClienteEsperadoTambemEValida() throws Exception {
        configurarPayload(payloadValido().setAudience(List.of("outro", CLIENT_ID)));

        assertThat(adapter.verificar("token").subject()).isEqualTo("sub-123");
    }

    private GoogleIdToken.Payload payloadValido() {
        return new GoogleIdToken.Payload()
                .setSubject("sub-123")
                .setEmail("pessoa@example.com")
                .setEmailVerified(true)
                .setIssuer("https://accounts.google.com")
                .setAudience(CLIENT_ID)
                .setExpirationTimeSeconds(AGORA.plusSeconds(600).getEpochSecond())
                .set("name", "Pessoa Google")
                .set("picture", "https://img.example/avatar");
    }

    private void configurarPayload(GoogleIdToken.Payload payload) throws Exception {
        when(verifier.verify("token")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);
    }

    private void assertFalhaGenerica(String token) {
        assertThatThrownBy(() -> adapter.verificar(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(GoogleTokenVerifier.MENSAGEM_ERRO);
    }
}
