package com.portifolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portifolio.exception.UnauthorizedException;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.model.enums.StatusConta;
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
            .withInitScripts("db/schema-test.sql", "db/catalogo-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PerfilArtistaRepository perfilArtistaRepository;
    @Autowired PerfilContratanteRepository perfilContratanteRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired FakeGoogleTokenVerifier fakeVerifier;
    @Autowired com.portifolio.security.JwtService jwtService;
    @Autowired com.portifolio.service.RefreshTokenService refreshTokenService;
    @Autowired com.portifolio.security.UserDetailsServiceImpl userDetailsService;
    @Autowired com.portifolio.config.StompJwtChannelInterceptor stompInterceptor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void estadosPendentesNaoSaoPromovidosENaoAceitamSenhaRefreshOuJwtAnterior() throws Exception {
        for (StatusConta estado : new StatusConta[]{StatusConta.PENDENTE_TIPO_PERFIL,
                StatusConta.PENDENTE_VERIFICACAO_EMAIL, StatusConta.PENDENTE_CONSENTIMENTO,
                StatusConta.BLOQUEADA}) {
            Usuario usuario = usuarioLocal(estado.name() + "@palco.test", "Senha@2026", TipoUsuario.ARTISTA);
            String jwtAnterior = jwtService.gerarToken(usuario);
            String refreshAnterior = refreshTokenService.gerarRefreshToken(usuario);
            usuario.setStatusConta(estado);
            usuarioRepository.saveAndFlush(usuario);
            fakeVerifier.aceitar(estado.name(), claims("google-" + estado.name(), usuario.getEmail()));

            var loginGoogle = mockMvc.perform(postGoogle(payloadCompleto(estado.name(), "CONTRATANTE", true)));
            if (estado == StatusConta.BLOQUEADA) {
                loginGoogle.andExpect(status().isForbidden());
                // A transação rejeitada não vincula o Google. Configura um vínculo já existente
                // para verificar também os outros meios de autenticação da conta bloqueada.
                usuario.setGoogleId("google-" + estado.name());
                usuarioRepository.saveAndFlush(usuario);
            } else {
                loginGoogle.andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("AGUARDANDO_DADOS"))
                        .andExpect(jsonPath("$.statusConta").value(estado.name()))
                        .andExpect(jsonPath("$.token").isEmpty())
                        .andExpect(jsonPath("$.refreshToken").isEmpty());
                // Repetir pelo google_id já vinculado também não promove a conta.
                mockMvc.perform(postGoogle(payloadCompleto(estado.name(), "CONTRATANTE", true)))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.token").isEmpty());
            }
            Usuario depois = usuarioRepository.findById(usuario.getId()).orElseThrow();
            assertThat(depois.getStatusConta()).isEqualTo(estado);
            assertThat(depois.getTipoUsuario()).isEqualTo(TipoUsuario.ARTISTA);
            assertThat(depois.getSenha()).isEqualTo(usuario.getSenha());
            mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + jwtAnterior))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", usuario.getEmail(), "senha", "Senha@2026"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshAnterior))))
                    .andExpect(status().isForbidden());
            var frame = org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(
                    org.springframework.messaging.simp.stomp.StompCommand.CONNECT);
            frame.setNativeHeader("Authorization", "Bearer " + jwtAnterior);
            frame.setLeaveMutable(true);
            var message = org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], frame.getMessageHeaders());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> stompInterceptor.preSend(message, null))
                    .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
        }
        // Somente os quatro refresh antigos de fixture; nenhum novo foi emitido.
        assertThat(refreshTokenRepository.count()).isEqualTo(4);
        assertThat(perfilArtistaRepository.count()).isZero();
    }

    @Test
    void contaAtivaComPerfilIncompletoNaoRecebeAcessoNormal() throws Exception {
        Usuario usuario = usuarioLocal("incompleta-existente@palco.test", "Senha@2026", TipoUsuario.ARTISTA);
        usuario.setGoogleId("google-perfil-incompleto");
        usuario.setPerfilCompleto(false);
        usuarioRepository.saveAndFlush(usuario);
        fakeVerifier.aceitar("perfil-incompleto", claims(usuario.getGoogleId(), usuario.getEmail()));
        mockMvc.perform(postGoogle(Map.of("idToken", "perfil-incompleto", "rememberMe", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_DADOS"))
                .andExpect(jsonPath("$.statusConta").value("ATIVA"))
                .andExpect(jsonPath("$.perfilCompleto").value(false))
                .andExpect(jsonPath("$.token").isEmpty())
                .andExpect(jsonPath("$.refreshToken").isEmpty());
        assertThat(usuarioRepository.findById(usuario.getId()).orElseThrow().getPerfilCompleto()).isFalse();
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void contaGoogleOnlyAptaExistenteContinuaAcessandoNormalmente() throws Exception {
        Usuario usuario = usuarioLocal("google-apto@palco.test", "Senha@2026", TipoUsuario.ARTISTA);
        usuario.setGoogleId("google-apto");
        usuario.setSenha(null);
        usuarioRepository.saveAndFlush(usuario);
        fakeVerifier.aceitar("apto", claims(usuario.getGoogleId(), usuario.getEmail()));
        MvcResult resultado = mockMvc.perform(postGoogle(Map.of("idToken", "apto", "rememberMe", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTENTICADO"))
                .andExpect(jsonPath("$.statusConta").value("ATIVA"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty()).andReturn();
        JsonNode resposta = objectMapper.readTree(resultado.getResponse().getContentAsString());
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + resposta.get("token").asText()))
                .andExpect(status().isOk());
        assertThat(usuarioRepository.findById(usuario.getId()).orElseThrow().getSenha()).isNull();
    }

    @Test
    void provisoriaNaoEmiteRefreshMesmoComRememberMeTrue() throws Exception {
        fakeVerifier.aceitar("provisoria", claims("google-provisoria", "provisoria@palco.test"));
        mockMvc.perform(postGoogle(payloadCompleto("provisoria", "ARTISTA", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusConta").value("PENDENTE_TIPO_PERFIL"))
                .andExpect(jsonPath("$.token").isEmpty())
                .andExpect(jsonPath("$.refreshToken").isEmpty());
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @BeforeEach
    void limparBancoEFake() {
        fakeVerifier.limpar();
        jdbcTemplate.execute("""
                truncate table refresh_tokens, perfis_artistas, perfis_contratantes, usuarios
                restart identity cascade
                """);
    }

    @Test
    void credencialValidaCriaContaPendenteSemSenhaPerfilDefinitivoOuTokens() throws Exception {
        fakeVerifier.aceitar("novo", claims("google-novo", "novo@palco.test"));

        mockMvc.perform(postGoogle(payloadCompleto("novo", "ARTISTA", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_DADOS"))
                .andExpect(jsonPath("$.statusConta").value("PENDENTE_TIPO_PERFIL"))
                .andExpect(jsonPath("$.token").isEmpty())
                .andExpect(jsonPath("$.refreshToken").isEmpty())
                .andExpect(jsonPath("$.tipoUsuario").value("ARTISTA"));

        Usuario salvo = usuarioRepository.findByEmail("novo@palco.test").orElseThrow();
        assertThat(salvo.getSenha()).isNull();
        assertThat(salvo.getGoogleId()).isEqualTo("google-novo");
        assertThat(salvo.getStatusConta()).isEqualTo(StatusConta.PENDENTE_TIPO_PERFIL);
        assertThat(salvo.getEmailVerificado()).isTrue();
        assertThat(salvo.getPerfilCompleto()).isFalse();
        assertThat(salvo.getNome()).isEqualTo("Pessoa Google");
        assertThat(salvo.getFotoPerfil()).isEqualTo("https://img.example/avatar");
        assertThat(perfilArtistaRepository.findById(salvo.getId())).isEmpty();
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
        usuarioLocal("com-refresh@palco.test", "Senha@2026", TipoUsuario.CONTRATANTE);
        fakeVerifier.aceitar("com-refresh", claims("google-com-refresh", "com-refresh@palco.test"));

        mockMvc.perform(postGoogle(payloadCompleto("com-refresh", "CONTRATANTE", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTENTICADO"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        assertThat(refreshTokenRepository.count()).isOne();
    }

    @Test
    void schemaImpedeProvisorioSemDadosERespostaNaoEmiteAcesso()
            throws Exception {
        fakeVerifier.aceitar("incompleto", claims("google-incompleto", "incompleto@palco.test"));

        mockMvc.perform(postGoogle(Map.of("idToken", "incompleto", "rememberMe", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_DADOS"))
                .andExpect(jsonPath("$.token").isEmpty())
                .andExpect(jsonPath("$.refreshToken").isEmpty())
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
    void duasAutenticacoesSimultaneasCriamUmaSoContaPendenteSemPerfil() throws Exception {
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
            assertThat(respostaA.get("statusConta").asText()).isEqualTo("PENDENTE_TIPO_PERFIL");
            assertThat(respostaB.get("statusConta").asText()).isEqualTo("PENDENTE_TIPO_PERFIL");
            assertThat(respostaA.get("token").isNull()).isTrue();
            assertThat(respostaB.get("token").isNull()).isTrue();
        } finally {
            iniciar.countDown();
            executor.shutdownNow();
        }

        assertThat(usuarioRepository.count()).isOne();
        assertThat(usuarioRepository.findByEmail("concorrente@palco.test").orElseThrow().getSenha())
                .isNull();
        assertThat(perfilArtistaRepository.count()).isZero();
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
                "tipoPerfilArtistico", "ARTISTA_SOLO",
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
        usuario.setPerfilCompleto(true);
        usuario.setStatusConta(StatusConta.ATIVA);
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
