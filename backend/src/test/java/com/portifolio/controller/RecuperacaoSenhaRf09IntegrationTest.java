package com.portifolio.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.service.PasswordRecoveryEmailSender;
import com.portifolio.service.PasswordRecoveryService;
import com.portifolio.validation.PasswordPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(RecuperacaoSenhaRf09IntegrationTest.EmailTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class RecuperacaoSenhaRf09IntegrationTest {

    private static final String CONSTRAINT_ROLLBACK = "rf09_forcar_rollback_refresh";
    private static final String SENHA_ANTIGA = "Antiga@2026";
    private static final String SENHA_NOVA = "Nova@2026";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScript("db/schema-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired CapturingEmailSender emailSender;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void limparBanco() {
        jdbcTemplate.execute("ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS "
                + CONSTRAINT_ROLLBACK);
        jdbcTemplate.execute("TRUNCATE refresh_tokens, perfis_artistas, perfis_contratantes, "
                + "usuarios RESTART IDENTITY CASCADE");
        emailSender.limpar();
    }

    @Test
    void respostasDevemSerIndistinguiveisParaContaLocalInexistenteEGoogleOnly()
            throws Exception {
        Usuario local = novoUsuarioLocal("local@rf09.test");
        Usuario google = novoUsuarioGoogleOnly("google@rf09.test");

        MvcResult existente = solicitar(local.getEmail());
        MvcResult inexistente = solicitar("inexistente@rf09.test");
        MvcResult googleOnly = solicitar(google.getEmail());

        assertThat(existente.getResponse().getContentAsString())
                .isEqualTo(inexistente.getResponse().getContentAsString())
                .isEqualTo(googleOnly.getResponse().getContentAsString());
        assertThat(existente.getResponse().getContentAsString())
                .doesNotContain(local.getId().toString(), "token", "expiracao", "GOOGLE");
        assertThat(usuarioRepository.findById(google.getId()).orElseThrow().getTokenRecuperacao())
                .isNull();
        assertThat(emailSender.linksEnviados).isEqualTo(1);
        assertThat(emailSender.orientacoesGoogle).isEqualTo(1);
    }

    @Test
    void emailInexistenteNaoDeveCriarNemAlterarDados() throws Exception {
        solicitar("nao-existe@rf09.test");

        assertThat(usuarioRepository.count()).isZero();
        assertThat(emailSender.linksEnviados).isZero();
        assertThat(emailSender.orientacoesGoogle).isZero();
    }

    @Test
    void solicitacaoLocalDevePersistirSomenteHashComValidadeDeUmaHora()
            throws Exception {
        Usuario usuario = novoUsuarioLocal("hash@rf09.test");
        LocalDateTime antes = LocalDateTime.now();

        MvcResult resultado = solicitar(usuario.getEmail());

        LocalDateTime depois = LocalDateTime.now();
        Usuario persistido = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(emailSender.ultimoToken).isNotBlank().hasSize(43);
        assertThat(persistido.getTokenRecuperacao())
                .isEqualTo(hash(emailSender.ultimoToken))
                .isNotEqualTo(emailSender.ultimoToken)
                .hasSize(64);
        assertThat(persistido.getTokenExpiracao())
                .isBetween(antes.plusMinutes(59), depois.plusMinutes(61));
        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain(emailSender.ultimoToken, persistido.getTokenRecuperacao());
    }

    @Test
    void novaSolicitacaoDeveInvalidarTokenAnterior() throws Exception {
        Usuario usuario = novoUsuarioLocal("substituicao@rf09.test");
        solicitar(usuario.getEmail());
        String primeiro = emailSender.ultimoToken;

        solicitar(usuario.getEmail());
        String segundo = emailSender.ultimoToken;

        assertThat(segundo).isNotEqualTo(primeiro);
        assertThat(usuarioRepository.findById(usuario.getId()).orElseThrow().getTokenRecuperacao())
                .isEqualTo(hash(segundo));
        redefinir(primeiro, SENHA_NOVA).andExpect(status().isNotFound());
        redefinir(segundo, SENHA_NOVA).andExpect(status().isOk());
    }

    @Test
    void redefinicaoValidaDeveUsarBCryptLimparTokenESerDeUsoUnico()
            throws Exception {
        Usuario usuario = novoUsuarioLocal("redefinicao@rf09.test");
        solicitar(usuario.getEmail());
        String token = emailSender.ultimoToken;

        redefinir(token, SENHA_NOVA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("Senha redefinida com sucesso."))
                .andExpect(jsonPath("$.token").doesNotExist());

        Usuario persistido = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(persistido.getSenha()).startsWith("$2").doesNotContain(SENHA_NOVA);
        assertThat(passwordEncoder.matches(SENHA_NOVA, persistido.getSenha())).isTrue();
        assertThat(passwordEncoder.matches(SENHA_ANTIGA, persistido.getSenha())).isFalse();
        assertThat(persistido.getTokenRecuperacao()).isNull();
        assertThat(persistido.getTokenExpiracao()).isNull();
        redefinir(token, "outra-senha").andExpect(status().isNotFound());
        login(usuario.getEmail(), SENHA_ANTIGA, false).andExpect(status().isUnauthorized());
        login(usuario.getEmail(), SENHA_NOVA, false).andExpect(status().isOk());
    }

    @Test
    void senhaInvalidaNaoAlteraHashConsomeTokenOuRevogaSessaoEPermiteNovaTentativa()
            throws Exception {
        Usuario usuario = novoUsuarioLocal("politica@rf09.test");
        JsonNode login = corpo(login(usuario.getEmail(), SENHA_ANTIGA, true)
                .andExpect(status().isOk()).andReturn());
        String refreshToken = login.get("refreshToken").asText();
        solicitar(usuario.getEmail());
        String token = emailSender.ultimoToken;
        Usuario antes = usuarioRepository.findById(usuario.getId()).orElseThrow();
        String hashAnterior = antes.getSenha();
        String tokenHash = antes.getTokenRecuperacao();
        LocalDateTime expiracao = antes.getTokenExpiracao();

        redefinir(token, "artista123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(PasswordPolicy.MESSAGE));

        Usuario aposFalha = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(aposFalha.getSenha()).isEqualTo(hashAnterior);
        assertThat(aposFalha.getTokenRecuperacao()).isEqualTo(tokenHash);
        assertThat(aposFalha.getTokenExpiracao()).isEqualTo(expiracao);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk());

        redefinir(token, SENHA_NOVA).andExpect(status().isOk());
        assertThat(passwordEncoder.matches(
                SENHA_NOVA, usuarioRepository.findById(usuario.getId()).orElseThrow().getSenha()))
                .isTrue();
    }

    @Test
    void limiteTecnicoDoBCryptPreservaHashTokenESessaoEPermiteReusarMesmoToken()
            throws Exception {
        Usuario usuario = novoUsuarioLocal("bcrypt@rf09.test");
        JsonNode login = corpo(login(usuario.getEmail(), SENHA_ANTIGA, true)
                .andExpect(status().isOk()).andReturn());
        String refreshToken = login.get("refreshToken").asText();
        solicitar(usuario.getEmail());
        String token = emailSender.ultimoToken;
        Usuario antes = usuarioRepository.findById(usuario.getId()).orElseThrow();
        String hashAnterior = antes.getSenha();
        String tokenHash = antes.getTokenRecuperacao();
        LocalDateTime expiracao = antes.getTokenExpiracao();

        String resposta = redefinir(token, "Aa1!" + "á".repeat(35))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(PasswordPolicy.BCRYPT_LIMIT_MESSAGE))
                .andReturn().getResponse().getContentAsString();

        Usuario aposFalha = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(resposta).doesNotContain("BCrypt", "72 bytes", "password cannot", token);
        assertThat(aposFalha.getSenha()).isEqualTo(hashAnterior);
        assertThat(aposFalha.getTokenRecuperacao()).isEqualTo(tokenHash);
        assertThat(aposFalha.getTokenExpiracao()).isEqualTo(expiracao);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk());

        redefinir(token, SENHA_NOVA).andExpect(status().isOk());
        assertThat(passwordEncoder.matches(
                SENHA_NOVA, usuarioRepository.findById(usuario.getId()).orElseThrow().getSenha()))
                .isTrue();
    }

    @Test
    void tokenInvalidoEExpiradoDevemTerMesmoErroSemAlterarSenha() throws Exception {
        Usuario usuario = novoUsuarioLocal("expirado@rf09.test");
        usuario.setTokenRecuperacao(hash("token-expirado"));
        usuario.setTokenExpiracao(LocalDateTime.now().minusNanos(1));
        usuarioRepository.save(usuario);

        JsonNode erroInvalido = corpo(redefinir("token-inexistente", SENHA_NOVA)
                .andExpect(status().isNotFound())
                .andReturn());
        JsonNode erroExpirado = corpo(redefinir("token-expirado", SENHA_NOVA)
                .andExpect(status().isNotFound())
                .andReturn());

        assertThat(erroExpirado.get("status")).isEqualTo(erroInvalido.get("status"));
        assertThat(erroExpirado.get("mensagem")).isEqualTo(erroInvalido.get("mensagem"));
        assertThat(erroExpirado.get("detalhes")).isEqualTo(erroInvalido.get("detalhes"));
        assertThat(passwordEncoder.matches(
                SENHA_ANTIGA, usuarioRepository.findById(usuario.getId()).orElseThrow().getSenha()))
                .isTrue();
    }

    @Test
    void tokenEmQueryParameterSemTokenNoBodyDeveSerRejeitado() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .queryParam("token", "na-query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("novaSenha", SENHA_NOVA))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redefinicaoDeveRevogarRefreshTokensAnteriores() throws Exception {
        Usuario usuario = novoUsuarioLocal("refresh@rf09.test");
        JsonNode login = corpo(login(usuario.getEmail(), SENHA_ANTIGA, true)
                .andExpect(status().isOk()).andReturn());
        String refreshToken = login.get("refreshToken").asText();
        solicitar(usuario.getEmail());

        redefinir(emailSender.ultimoToken, SENHA_NOVA).andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where usuario_id = ? and ativo = true",
                Integer.class, usuario.getId())).isZero();
    }

    @Test
    void falhaNaRevogacaoDeveFazerRollbackDaSenhaELimpezaDoToken()
            throws Exception {
        Usuario usuario = novoUsuarioLocal("rollback@rf09.test");
        login(usuario.getEmail(), SENHA_ANTIGA, true).andExpect(status().isOk());
        solicitar(usuario.getEmail());
        String token = emailSender.ultimoToken;
        jdbcTemplate.execute("ALTER TABLE refresh_tokens ADD CONSTRAINT "
                + CONSTRAINT_ROLLBACK + " CHECK (ativo = true)");

        redefinir(token, SENHA_NOVA).andExpect(status().isConflict());

        jdbcTemplate.execute("ALTER TABLE refresh_tokens DROP CONSTRAINT "
                + CONSTRAINT_ROLLBACK);
        Usuario persistido = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(SENHA_ANTIGA, persistido.getSenha())).isTrue();
        assertThat(persistido.getTokenRecuperacao()).isEqualTo(hash(token));
        assertThat(persistido.getTokenExpiracao()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where usuario_id = ? and ativo = true",
                Integer.class, usuario.getId())).isEqualTo(1);
    }

    @Test
    void falhaDeEntregaDeEmailDeveResponderGenericamenteESemTokenAtivo()
            throws Exception {
        Usuario usuario = novoUsuarioLocal("falha-email@rf09.test");
        emailSender.falhar = true;

        solicitar(usuario.getEmail());

        Usuario persistido = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(persistido.getTokenRecuperacao()).isNull();
        assertThat(persistido.getTokenExpiracao()).isNull();
    }

    @Test
    void falhaDeEntregaNaoDeveRegistrarTokenBruto(CapturedOutput output)
            throws Exception {
        Usuario usuario = novoUsuarioLocal("log-seguro@rf09.test");
        emailSender.falhar = true;

        solicitar(usuario.getEmail());

        assertThat(emailSender.ultimoToken).isNotBlank();
        assertThat(output.getAll()).doesNotContain(emailSender.ultimoToken);
    }

    private MvcResult solicitar(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value(PasswordRecoveryService.MENSAGEM_GENERICA))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.tipoUsuario").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.expiracao").doesNotExist())
                .andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions redefinir(
            String token, String novaSenha) throws Exception {
        return mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", token, "novaSenha", novaSenha))));
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String email, String senha, boolean rememberMe) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", email,
                        "senha", senha,
                        "rememberMe", rememberMe))));
    }

    private Usuario novoUsuarioLocal(String email) {
        Usuario usuario = usuarioBase(email);
        usuario.setSenha(passwordEncoder.encode(SENHA_ANTIGA));
        return usuarioRepository.save(usuario);
    }

    private Usuario novoUsuarioGoogleOnly(String email) {
        Usuario usuario = usuarioBase(email);
        usuario.setSenha(null);
        usuario.setGoogleId("google-" + email);
        return usuarioRepository.save(usuario);
    }

    private Usuario usuarioBase(String email) {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuário RF09");
        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setTelefone("11999999999");
        usuario.setEmail(email);
        usuario.setTipoUsuario(TipoUsuario.ARTISTA);
        usuario.setPerfilCompleto(true);
        usuario.setDataCriacao(LocalDateTime.now());
        return usuario;
    }

    private String json(Object valor) throws Exception {
        return objectMapper.writeValueAsString(valor);
    }

    private JsonNode corpo(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private String hash(String token) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EmailTestConfig {

        @Bean
        @Primary
        CapturingEmailSender passwordRecoveryEmailSender() {
            return new CapturingEmailSender();
        }
    }

    static class CapturingEmailSender implements PasswordRecoveryEmailSender {

        String ultimoToken;
        int linksEnviados;
        int orientacoesGoogle;
        boolean falhar;

        @Override
        public void enviarLinkRedefinicao(String email, String token) {
            ultimoToken = token;
            if (falhar) {
                throw new IllegalStateException("Falha simulada de entrega");
            }
            linksEnviados++;
        }

        @Override
        public void enviarOrientacaoLoginGoogle(String email) {
            orientacoesGoogle++;
        }

        void limpar() {
            ultimoToken = null;
            linksEnviados = 0;
            orientacoesGoogle = 0;
            falhar = false;
        }
    }
}
