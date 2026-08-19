package com.portifolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.RefreshTokenRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.security.JwtService;
import com.portifolio.service.RefreshTokenService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PerfilEdicaoRf08IntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScript("db/schema-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @Autowired MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PerfilArtistaRepository perfilArtistaRepository;
    @Autowired PerfilContratanteRepository perfilContratanteRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired RefreshTokenService refreshTokenService;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    @AfterEach
    void limparBanco() {
        jdbcTemplate.execute("TRUNCATE candidaturas, vagas, tags, perfis_artistas, "
                + "perfis_contratantes, refresh_tokens, usuarios RESTART IDENTITY CASCADE");
    }

    @Test
    void perfilArtistaExigeAutenticacaoPropriedadeETipoCorreto() throws Exception {
        PerfilArtista artista = novoArtista("artista-proprio-rf08@teste.com");
        PerfilArtista outro = novoArtista("artista-alheio-rf08@teste.com");
        PerfilContratante contratante = novoContratante("contratante-rf08@teste.com");
        String corpo = json(perfilArtistaPayload(outro.getUsuarioId()));

        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getUsuarioId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getUsuarioId())
                        .header("Authorization", bearer(outro.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/perfis-artistas/{id}", contratante.getUsuarioId())
                        .header("Authorization", bearer(contratante.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void artistaDerivaIdentidadeDoJwtIgnoraCamposAdministrativosECalculaCompletude() throws Exception {
        PerfilArtista artista = novoArtista("artista-completo-rf08@teste.com");
        PerfilArtista outro = novoArtista("artista-id-payload-rf08@teste.com");
        artista.setNivelMedalha(2);
        artista.setScoreEngajamento(new BigDecimal("10.00"));
        perfilArtistaRepository.save(artista);
        Map<String, Object> payload = perfilArtistaPayload(outro.getUsuarioId());
        payload.put("nivelMedalha", 5);
        payload.put("scoreEngajamento", 99.99);
        payload.put("perfilCompleto", true);

        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getUsuarioId())
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(artista.getUsuarioId()));

        PerfilArtista salvo = perfilArtistaRepository.findById(artista.getUsuarioId()).orElseThrow();
        assertThat(salvo.getNivelMedalha()).isEqualTo(2);
        assertThat(salvo.getScoreEngajamento()).isEqualByComparingTo("10.00");
        assertThat(usuarioRepository.findById(artista.getUsuarioId()).orElseThrow()
                .getPerfilCompleto()).isTrue();
        // Portfólio e tags não fazem parte da lista obrigatória definida para o RF08.
        assertThat(salvo.getUrlPortfolio()).isNull();
        assertThat(salvo.getTags()).isEmpty();
    }

    @Test
    void camposOmitidosSaoPreservadosEUrlsInvalidasRetornam400() throws Exception {
        PerfilArtista artista = novoArtista("artista-parcial-rf08@teste.com");
        artista.setBiografia("Biografia anterior");
        artista.setLocalizacao("Local anterior");
        artista.setUrlPortfolio("https://portfolio.example");
        perfilArtistaRepository.save(artista);

        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getUsuarioId())
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("biografia", "Biografia nova"))))
                .andExpect(status().isOk());

        PerfilArtista salvo = perfilArtistaRepository.findById(artista.getUsuarioId()).orElseThrow();
        assertThat(salvo.getLocalizacao()).isEqualTo("Local anterior");
        assertThat(salvo.getUrlPortfolio()).isEqualTo("https://portfolio.example");

        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getUsuarioId())
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("bannerUrl", "url-invalida"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void contratanteSoEditaProprioPerfilENomeEmpresaEhOpcional() throws Exception {
        PerfilContratante dono = novoContratante("contratante-dono-rf08@teste.com");
        PerfilContratante outro = novoContratante("contratante-outro-rf08@teste.com");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("usuarioId", outro.getUsuarioId());
        payload.put("biografia", "Biografia completa");
        payload.put("localizacao", "São Paulo, SP");

        mockMvc.perform(put("/api/perfis-contratantes/{id}", dono.getUsuarioId())
                        .header("Authorization", bearer(outro.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/perfis-contratantes/{id}", dono.getUsuarioId())
                        .header("Authorization", bearer(dono.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isOk());

        PerfilContratante salvo = perfilContratanteRepository
                .findById(dono.getUsuarioId()).orElseThrow();
        assertThat(salvo.getNomeEmpresa()).isNull();
        assertThat(usuarioRepository.findById(dono.getUsuarioId()).orElseThrow()
                .getPerfilCompleto()).isTrue();
    }

    @Test
    void rotaLegadaDeUsuarioSoEditaProprioUsuarioEIgnoraCamposProtegidos() throws Exception {
        PerfilArtista dono = novoArtista("usuario-dono-rf08@teste.com");
        PerfilArtista outro = novoArtista("usuario-outro-rf08@teste.com");
        LocalDate nascimento = dono.getUsuario().getDataNascimento();
        Map<String, Object> payload = dadosUsuario(dono.getUsuario());
        payload.put("dataNascimento", "2005-05-05");
        payload.put("tipoUsuario", "CONTRATANTE");
        payload.put("perfilCompleto", true);
        payload.put("tokenRecuperacao", "administrativo");

        mockMvc.perform(put("/api/usuarios/{id}", dono.getUsuarioId())
                        .header("Authorization", bearer(outro.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/usuarios/{id}", dono.getUsuarioId())
                        .header("Authorization", bearer(dono.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isOk());

        Usuario salvo = usuarioRepository.findById(dono.getUsuarioId()).orElseThrow();
        assertThat(salvo.getDataNascimento()).isEqualTo(nascimento);
        assertThat(salvo.getTipoUsuario()).isEqualTo(TipoUsuario.ARTISTA);
        assertThat(salvo.getTokenRecuperacao()).isNull();
        assertThat(salvo.getPerfilCompleto()).isFalse();
    }

    @Test
    void respostaDeTerceiroNaoExpoeDadosPessoaisOuResponsavel() throws Exception {
        PerfilArtista consultado = novoArtista("privado-consultado-rf08@teste.com");
        PerfilContratante consulente = novoContratante("privado-consulente-rf08@teste.com");
        consultado.getUsuario().setNomeResponsavel("Responsável privado");
        consultado.getUsuario().setTelefoneResponsavel("11555554444");
        consultado.getUsuario().setEmailResponsavel("responsavel-privado@teste.com");
        usuarioRepository.save(consultado.getUsuario());

        mockMvc.perform(get("/api/usuarios/{id}", consultado.getUsuarioId())
                        .header("Authorization", bearer(consulente.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Usuário RF08"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefone").doesNotExist())
                .andExpect(jsonPath("$.dataNascimento").doesNotExist())
                .andExpect(jsonPath("$.nomeResponsavel").doesNotExist())
                .andExpect(jsonPath("$.telefoneResponsavel").doesNotExist())
                .andExpect(jsonPath("$.emailResponsavel").doesNotExist());
    }

    @Test
    void trocaDeSenhaExigeSenhaAtualCorreta() throws Exception {
        PerfilArtista artista = novoArtista("senha-exigida-rf08@teste.com");
        Map<String, Object> payload = dadosUsuario(artista.getUsuario());
        payload.put("novaSenha", "NovaSenha456!");

        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isUnprocessableEntity());
        payload.put("senhaAtual", "senha-incorreta");
        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    void trocaDeSenhaUsaBCryptERevogaTodosRefreshTokens() throws Exception {
        PerfilArtista artista = novoArtista("senha-sucesso-rf08@teste.com");
        refreshTokenService.gerarRefreshToken(artista.getUsuario());
        refreshTokenService.gerarRefreshToken(artista.getUsuario());
        Map<String, Object> payload = dadosUsuario(artista.getUsuario());
        payload.put("senhaAtual", "SenhaAtual123!");
        payload.put("novaSenha", "NovaSenha456!");

        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isOk());

        Usuario salvo = usuarioRepository.findById(artista.getUsuarioId()).orElseThrow();
        assertThat(salvo.getSenha()).isNotEqualTo("NovaSenha456!");
        assertThat(passwordEncoder.matches("NovaSenha456!", salvo.getSenha())).isTrue();
        assertThat(refreshTokenRepository.findAll()).allMatch(token -> !token.getAtivo());
    }

    @Test
    void contaExclusivamenteGoogleNaoCriaSenhaLocalPorEsteFluxo() throws Exception {
        PerfilArtista artista = novoArtista("google-rf08@teste.com");
        artista.getUsuario().setSenha(null);
        artista.getUsuario().setGoogleId("google-rf08-id");
        usuarioRepository.save(artista.getUsuario());
        Map<String, Object> payload = dadosUsuario(artista.getUsuario());
        payload.put("senhaAtual", "qualquer");
        payload.put("novaSenha", "NovaSenha456!");

        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isUnprocessableEntity());
        assertThat(usuarioRepository.findById(artista.getUsuarioId()).orElseThrow().getSenha())
                .isNull();
    }

    @Test
    void menorExigeResponsavelLegalEPreservaCamposOmitidos() throws Exception {
        PerfilArtista menor = novoArtista("menor-rf08@teste.com");
        menor.getUsuario().setDataNascimento(LocalDate.now().minusYears(16));
        usuarioRepository.save(menor.getUsuario());

        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(menor.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dadosUsuario(menor.getUsuario()))))
                .andExpect(status().isUnprocessableEntity());

        menor.getUsuario().setNomeResponsavel("Responsável Original");
        menor.getUsuario().setTelefoneResponsavel("11911112222");
        menor.getUsuario().setEmailResponsavel("responsavel-original@teste.com");
        usuarioRepository.save(menor.getUsuario());
        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(menor.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dadosUsuario(menor.getUsuario()))))
                .andExpect(status().isOk());

        Usuario salvo = usuarioRepository.findById(menor.getUsuarioId()).orElseThrow();
        assertThat(salvo.getNomeResponsavel()).isEqualTo("Responsável Original");
        assertThat(salvo.getTelefoneResponsavel()).isEqualTo("11911112222");
        assertThat(salvo.getEmailResponsavel()).isEqualTo("responsavel-original@teste.com");
    }

    @Test
    void cadastroDeMenorValidaResponsavelEDataFutura() throws Exception {
        Map<String, Object> menorSemResponsavel = cadastroPayload(
                "menor-cadastro-rf08@teste.com", LocalDate.now().minusYears(16));
        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(menorSemResponsavel)))
                .andExpect(status().isBadRequest());

        menorSemResponsavel.put("nomeResponsavel", "Responsável");
        menorSemResponsavel.put("telefoneResponsavel", "11988887777");
        menorSemResponsavel.put("emailResponsavel", "responsavel@teste.com");
        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(menorSemResponsavel)))
                .andExpect(status().isCreated());

        Map<String, Object> futuro = cadastroPayload(
                "futuro-rf08@teste.com", LocalDate.now().plusDays(1));
        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON).content(json(futuro)))
                .andExpect(status().isBadRequest());
    }

    private PerfilArtista novoArtista(String email) {
        Usuario usuario = novoUsuario(email, TipoUsuario.ARTISTA);
        PerfilArtista perfil = new PerfilArtista();
        perfil.setUsuario(usuario);
        return perfilArtistaRepository.save(perfil);
    }

    private PerfilContratante novoContratante(String email) {
        Usuario usuario = novoUsuario(email, TipoUsuario.CONTRATANTE);
        PerfilContratante perfil = new PerfilContratante();
        perfil.setUsuario(usuario);
        return perfilContratanteRepository.save(perfil);
    }

    private Usuario novoUsuario(String email, TipoUsuario tipo) {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuário RF08");
        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setTelefone("11999999999");
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode("SenhaAtual123!"));
        usuario.setTipoUsuario(tipo);
        usuario.setPerfilCompleto(false);
        usuario.setDataCriacao(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    private Map<String, Object> perfilArtistaPayload(Long usuarioId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("usuarioId", usuarioId);
        payload.put("biografia", "Biografia completa");
        payload.put("localizacao", "São Paulo, SP");
        return payload;
    }

    private Map<String, Object> dadosUsuario(Usuario usuario) {
        return new LinkedHashMap<>(Map.of(
                "nome", usuario.getNome(),
                "telefone", usuario.getTelefone(),
                "email", usuario.getEmail()));
    }

    private Map<String, Object> cadastroPayload(String email, LocalDate nascimento) {
        return new LinkedHashMap<>(Map.of(
                "nome", "Menor RF08",
                "dataNascimento", nascimento.toString(),
                "telefone", "11999999999",
                "email", email,
                "senha", "SenhaAtual123!",
                "tipoUsuario", "ARTISTA"));
    }

    private String json(Object valor) throws Exception {
        return objectMapper.writeValueAsString(valor);
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.gerarToken(usuario);
    }
}
