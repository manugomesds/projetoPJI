package com.portifolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portifolio.exception.UnauthorizedException;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.RefreshTokenRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.service.google.GoogleTokenClaims;
import com.portifolio.service.google.GoogleTokenVerifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "app.vagas.auto-close.enabled=false")
@AutoConfigureMockMvc
@Import(GoogleAuthRf24HardeningIntegrationTest.GoogleVerifierTestConfig.class)
class GoogleAuthRf24HardeningIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScript("db/schema-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PerfilArtistaRepository perfilArtistaRepository;
    @Autowired PerfilContratanteRepository perfilContratanteRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired FakeGoogleTokenVerifier fakeVerifier;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void limparBancoEFake() {
        fakeVerifier.limpar();
        jdbcTemplate.execute("""
                truncate table refresh_tokens, perfis_artistas, perfis_contratantes, usuarios
                restart identity cascade
                """);
    }

    @Test
    void credencialValidaCriaContaGoogleOnlySemSenhaEPerfilInicial() throws Exception {
        fakeVerifier.aceitar("novo", claims("google-novo", "novo@palco.test"));

        mockMvc.perform(postGoogle(payloadCompleto("novo", "ARTISTA", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTENTICADO"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipoUsuario").value("ARTISTA"));

        Usuario salvo = usuarioRepository.findByEmail("novo@palco.test").orElseThrow();
        assertThat(salvo.getSenha()).isNull();
        assertThat(salvo.getGoogleId()).isEqualTo("google-novo");
        assertThat(perfilArtistaRepository.findById(salvo.getId())).isPresent();
        assertThat(perfilContratanteRepository.count()).isZero();
    }

    @Test
    void credencialInvalidaRetorna401GenericoSemVazamentoNemPersistencia() throws Exception {
        String tokenSensivel = "token-assinatura-invalida";
        fakeVerifier.rejeitar(tokenSensivel);

        MvcResult resultado = mockMvc.perform(postGoogle(payloadCompleto(
                        tokenSensivel, "ARTISTA", true)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value(GoogleTokenVerifier.MENSAGEM_ERRO))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain(tokenSensivel, "assinatura", "google.client-id", "@palco.test");
        assertThat(usuarioRepository.count()).isZero();
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void googleIdJaVinculadoReutilizaExatamenteMesmoUsuario() throws Exception {
        Usuario existente = usuarioLocal("existente@palco.test", "Hash@2026", TipoUsuario.ARTISTA);
        existente.setGoogleId("google-existente");
        existente = usuarioRepository.saveAndFlush(existente);
        fakeVerifier.aceitar("retorno", claims("google-existente", "existente@palco.test"));

        mockMvc.perform(postGoogle(payloadCompleto("retorno", "CONTRATANTE", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existente.getId()))
                .andExpect(jsonPath("$.tipoUsuario").value("ARTISTA"));

        assertThat(usuarioRepository.count()).isOne();
    }

    @Test
    void contaLocalMesmoEmailEVinculadaSemDuplicataEPreservaHashBcrypt() throws Exception {
        Usuario local = usuarioLocal("Pessoa.Local@Palco.Test", "Senha@Local2026", TipoUsuario.ARTISTA);
        String hashOriginal = local.getSenha();
        fakeVerifier.aceitar("vincular", claims("google-local", "pessoa.local@palco.test"));

        mockMvc.perform(postGoogle(payloadCompleto("vincular", "CONTRATANTE", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(local.getId()))
                .andExpect(jsonPath("$.tipoUsuario").value("ARTISTA"));

        Usuario depois = usuarioRepository.findById(local.getId()).orElseThrow();
        assertThat(usuarioRepository.count()).isOne();
        assertThat(depois.getGoogleId()).isEqualTo("google-local");
        assertThat(depois.getSenha()).isEqualTo(hashOriginal);
        assertThat(passwordEncoder.matches("Senha@Local2026", depois.getSenha())).isTrue();
    }

    @Test
    void autenticacaoPosteriorReutilizaVinculoCriado() throws Exception {
        Usuario local = usuarioLocal("posterior@palco.test", "Senha@Local2026", TipoUsuario.ARTISTA);
        fakeVerifier.aceitar("primeiro", claims("google-posterior", "posterior@palco.test"));
        fakeVerifier.aceitar("segundo", claims("google-posterior", "posterior@palco.test"));

        mockMvc.perform(postGoogle(payloadCompleto("primeiro", "ARTISTA", false)))
                .andExpect(status().isOk());
        mockMvc.perform(postGoogle(payloadCompleto("segundo", "CONTRATANTE", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(local.getId()));

        assertThat(usuarioRepository.count()).isOne();
    }

    @Test
    void googleIdEEmailApontandoUsuariosDiferentesFalhamSemMergeOuMutacao() throws Exception {
        Usuario porGoogle = usuarioLocal("um@palco.test", "Senha@Um2026", TipoUsuario.ARTISTA);
        porGoogle.setGoogleId("google-conflito");
        usuarioRepository.saveAndFlush(porGoogle);
        Usuario porEmail = usuarioLocal("dois@palco.test", "Senha@Dois2026", TipoUsuario.CONTRATANTE);
        String hashEmail = porEmail.getSenha();
        fakeVerifier.aceitar("conflito", claims("google-conflito", "dois@palco.test"));

        mockMvc.perform(postGoogle(payloadCompleto("conflito", "ARTISTA", true)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value(GoogleTokenVerifier.MENSAGEM_ERRO));

        assertThat(usuarioRepository.count()).isEqualTo(2);
        assertThat(usuarioRepository.findById(porGoogle.getId()).orElseThrow().getEmail())
                .isEqualTo("um@palco.test");
        Usuario emailDepois = usuarioRepository.findById(porEmail.getId()).orElseThrow();
        assertThat(emailDepois.getGoogleId()).isNull();
        assertThat(emailDepois.getSenha()).isEqualTo(hashEmail);
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void contaComGoogleIdDiferenteNaoESobrescrita() throws Exception {
        Usuario existente = usuarioLocal("vinculada@palco.test", "Senha@2026", TipoUsuario.ARTISTA);
        existente.setGoogleId("google-original");
        existente = usuarioRepository.saveAndFlush(existente);
        fakeVerifier.aceitar("troca", claims("google-novo", "vinculada@palco.test"));

        mockMvc.perform(postGoogle(payloadCompleto("troca", "CONTRATANTE", true)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value(GoogleTokenVerifier.MENSAGEM_ERRO));

        assertThat(usuarioRepository.findById(existente.getId()).orElseThrow().getGoogleId())
                .isEqualTo("google-original");
        assertThat(usuarioRepository.count()).isOne();
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void requestNaoAlteraTipoOuPerfilDaContaExistente() throws Exception {
        Usuario artista = usuarioLocal("tipo@palco.test", "Senha@2026", TipoUsuario.ARTISTA);
        fakeVerifier.aceitar("tipo", claims("google-tipo", "tipo@palco.test"));

        mockMvc.perform(postGoogle(payloadCompleto("tipo", "CONTRATANTE", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoUsuario").value("ARTISTA"));

        Usuario depois = usuarioRepository.findById(artista.getId()).orElseThrow();
        assertThat(depois.getTipoUsuario()).isEqualTo(TipoUsuario.ARTISTA);
        assertThat(perfilContratanteRepository.count()).isZero();
    }

    @Test
    void loginGoogleOnlyPosteriorFuncionaESenhaContinuaNull() throws Exception {
        fakeVerifier.aceitar("criar", claims("google-only", "only@palco.test"));
        fakeVerifier.aceitar("login", claims("google-only", "only@palco.test"));

        MvcResult criado = mockMvc.perform(postGoogle(payloadCompleto("criar", "ARTISTA", false)))
                .andExpect(status().isOk()).andReturn();
        long id = objectMapper.readTree(criado.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(postGoogle(Map.of("idToken", "login", "rememberMe", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        assertThat(usuarioRepository.findById(id).orElseThrow().getSenha()).isNull();
        assertThat(usuarioRepository.count()).isOne();
    }

    @Test
    void rememberMeFalseNaoCriaRefreshToken() throws Exception {
        fakeVerifier.aceitar("sem-refresh", claims("google-sem-refresh", "sem-refresh@palco.test"));

        mockMvc.perform(postGoogle(payloadCompleto("sem-refresh", "ARTISTA", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isEmpty());

        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void rememberMeTrueUsaRefreshTokenVigente() throws Exception {
        fakeVerifier.aceitar("com-refresh", claims("google-com-refresh", "com-refresh@palco.test"));

        mockMvc.perform(postGoogle(payloadCompleto("com-refresh", "CONTRATANTE", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        assertThat(refreshTokenRepository.count()).isOne();
    }

    @Test
    void novoUsuarioSemDadosComplementaresMantemComportamentoSemPersistirProvisorio()
            throws Exception {
        fakeVerifier.aceitar("incompleto", claims("google-incompleto", "incompleto@palco.test"));

        mockMvc.perform(postGoogle(Map.of("idToken", "incompleto", "rememberMe", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_DADOS"))
                .andExpect(jsonPath("$.emailGoogle").value("incompleto@palco.test"));

        assertThat(usuarioRepository.count()).isZero();
        assertThat(perfilArtistaRepository.count()).isZero();
        assertThat(perfilContratanteRepository.count()).isZero();
    }

    @Test
    void tokenInvalidoNaoAlteraVinculoExistente() throws Exception {
        Usuario local = usuarioLocal("imutavel@palco.test", "Senha@2026", TipoUsuario.ARTISTA);
        String hashOriginal = local.getSenha();
        fakeVerifier.rejeitar("invalido");

        mockMvc.perform(postGoogle(payloadCompleto("invalido", "CONTRATANTE", true)))
                .andExpect(status().isUnauthorized());

        Usuario depois = usuarioRepository.findById(local.getId()).orElseThrow();
        assertThat(depois.getGoogleId()).isNull();
        assertThat(depois.getSenha()).isEqualTo(hashOriginal);
        assertThat(depois.getTipoUsuario()).isEqualTo(TipoUsuario.ARTISTA);
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void duasAutenticacoesSimultaneasCriamUmaSoContaEUmSoPerfil() throws Exception {
        fakeVerifier.aceitar("concorrente", claims("google-concorrente", "concorrente@palco.test"));
        Map<String, Object> payload = payloadCompleto("concorrente", "ARTISTA", false);
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> primeira = executor.submit(
                    () -> autenticarAposBarreira(prontas, iniciar, payload));
            Future<MvcResult> segunda = executor.submit(
                    () -> autenticarAposBarreira(prontas, iniciar, payload));
            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();

            MvcResult resultadoA = primeira.get(20, TimeUnit.SECONDS);
            MvcResult resultadoB = segunda.get(20, TimeUnit.SECONDS);
            assertThat(resultadoA.getResponse().getStatus()).isEqualTo(200);
            assertThat(resultadoB.getResponse().getStatus()).isEqualTo(200);
            JsonNode respostaA = objectMapper.readTree(resultadoA.getResponse().getContentAsString());
            JsonNode respostaB = objectMapper.readTree(resultadoB.getResponse().getContentAsString());
            assertThat(respostaA.get("id").asLong()).isEqualTo(respostaB.get("id").asLong());
        } finally {
            iniciar.countDown();
            executor.shutdownNow();
        }

        assertThat(usuarioRepository.count()).isOne();
        assertThat(usuarioRepository.findByEmail("concorrente@palco.test").orElseThrow().getSenha())
                .isNull();
        assertThat(perfilArtistaRepository.count()).isOne();
        assertThat(perfilContratanteRepository.count()).isZero();
    }

    private MvcResult autenticarAposBarreira(
            CountDownLatch prontas, CountDownLatch iniciar, Map<String, Object> payload)
            throws Exception {
        prontas.countDown();
        iniciar.await();
        return mockMvc.perform(postGoogle(payload)).andReturn();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postGoogle(
            Map<String, Object> payload) throws Exception {
        return post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));
    }

    private Map<String, Object> payloadCompleto(String token, String tipo, boolean rememberMe) {
        return Map.of(
                "idToken", token,
                "rememberMe", rememberMe,
                "tipoUsuario", tipo,
                "dataNascimento", "1990-01-01",
                "telefone", "11999999999");
    }

    private GoogleTokenClaims claims(String googleId, String email) {
        return new GoogleTokenClaims(
                googleId, email, "Pessoa Google", "https://img.example/avatar");
    }

    private Usuario usuarioLocal(String email, String senha, TipoUsuario tipo) {
        Usuario usuario = new Usuario();
        usuario.setNome("Pessoa Local");
        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setTelefone("11988887777");
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(senha));
        usuario.setTipoUsuario(tipo);
        usuario.setPerfilCompleto(false);
        usuario.setDataCriacao(LocalDateTime.now());
        return usuarioRepository.saveAndFlush(usuario);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class GoogleVerifierTestConfig {

        @Bean
        @Primary
        FakeGoogleTokenVerifier fakeGoogleTokenVerifier() {
            return new FakeGoogleTokenVerifier();
        }
    }

    static class FakeGoogleTokenVerifier implements GoogleTokenVerifier {

        private final Map<String, GoogleTokenClaims> aceitos = new ConcurrentHashMap<>();

        void aceitar(String token, GoogleTokenClaims claims) {
            aceitos.put(token, claims);
        }

        void rejeitar(String token) {
            aceitos.remove(token);
        }

        void limpar() {
            aceitos.clear();
        }

        @Override
        public GoogleTokenClaims verificar(String idToken) {
            GoogleTokenClaims claims = aceitos.get(idToken);
            if (claims == null) {
                throw new UnauthorizedException(MENSAGEM_ERRO);
            }
            return claims;
        }
    }
}
