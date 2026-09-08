package com.portifolio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portifolio.model.Usuario;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.validation.PasswordPolicy;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
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
class AuthControllerRf01Rf02IntegrationTest {

    private static final String SENHA_VALIDA = "Palco@2026";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScript("db/schema-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void adultoPodeCadastrarArtistaEContratante() throws Exception {
        for (String tipo : new String[]{"ARTISTA", "CONTRATANTE"}) {
            Map<String, Object> payload = cadastroBase(tipo, LocalDate.now().minusYears(25));
            if (tipo.equals("CONTRATANTE")) {
                payload.put("tipoPerfilContratante", "Pessoa Física");
            }

            mockMvc.perform(post("/api/auth/cadastro")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tipoUsuario").value(tipo))
                    .andExpect(jsonPath("$.menorDeIdade").value(false));
        }
    }

    @Test
    void menorEntre14E17ExigeEPersisteResponsavel() throws Exception {
        Map<String, Object> payload = cadastroBase("ARTISTA", LocalDate.now().minusYears(16));
        payload.put("nomeResponsavel", "Responsável Legal");
        payload.put("telefoneResponsavel", "11988887777");
        payload.put("emailResponsavel", "responsavel-" + UUID.randomUUID() + "@palco.test");

        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.menorDeIdade").value(true));

        Usuario salvo = usuarioRepository.findByEmail((String) payload.get("email")).orElseThrow();
        assertThat(salvo.getNomeResponsavel()).isEqualTo("Responsável Legal");
        assertThat(salvo.getTelefoneResponsavel()).isEqualTo("11988887777");
        assertThat(salvo.getEmailResponsavel()).isEqualTo(payload.get("emailResponsavel"));
    }

    @Test
    void menorEntre14E17SemResponsavelERejeitado() throws Exception {
        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                cadastroBase("ARTISTA", LocalDate.now().minusYears(16)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(
                        "Nome do responsavel e obrigatorio para menores de 18 anos."));
    }

    @Test
    void menorDe14AnosERejeitado() throws Exception {
        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                cadastroBase("ARTISTA", LocalDate.now().minusYears(13)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A idade mínima para cadastro é 14 anos."));
    }

    @Test
    void emailDuplicadoRetorna409() throws Exception {
        Map<String, Object> payload = cadastroBase("ARTISTA", LocalDate.now().minusYears(30));
        cadastrar(payload);

        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Este email ja esta cadastrado."));
    }

    @Test
    void loginCorretoDevolveJwtESenhaPermaneceBcrypt() throws Exception {
        Map<String, Object> cadastro = cadastroBase("ARTISTA", LocalDate.now().minusYears(30));
        cadastrar(cadastro);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", cadastro.get("email"),
                                "senha", SENHA_VALIDA,
                                "rememberMe", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isEmpty())
                .andExpect(jsonPath("$.tipoUsuario").value("ARTISTA"));

        Usuario salvo = usuarioRepository.findByEmail((String) cadastro.get("email")).orElseThrow();
        assertThat(salvo.getSenha()).isNotEqualTo(SENHA_VALIDA);
        assertThat(passwordEncoder.matches(SENHA_VALIDA, salvo.getSenha())).isTrue();
    }

    @Test
    void cadastroRejeitaCadaCategoriaDeSenhaInvalidaSemPersistirUsuarioOuHash()
            throws Exception {
        String[] invalidas = {
            "Pa@1234",
            "Aa1!" + "x".repeat(69),
            "artista123",
            "PALCO@2026",
            "PalcoPalco!",
            "Palco2026",
            "        ",
            "Palco 2026",
            "Pálco2026"
        };

        for (String senha : invalidas) {
            Map<String, Object> payload = cadastroBase("ARTISTA", LocalDate.now().minusYears(30));
            payload.put("senha", senha);

            mockMvc.perform(post("/api/auth/cadastro")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value(PasswordPolicy.MESSAGE));

            assertThat(usuarioRepository.findByEmail((String) payload.get("email"))).isEmpty();
        }
    }

    @Test
    void emailInexistenteESenhaIncorretaRetornamMesmo401Generico() throws Exception {
        Map<String, Object> cadastro = cadastroBase("CONTRATANTE", LocalDate.now().minusYears(30));
        cadastrar(cadastro);

        for (Map<String, Object> login : new Map[]{
                Map.of("email", cadastro.get("email"), "senha", "incorreta", "rememberMe", false),
                Map.of("email", "inexistente-" + UUID.randomUUID() + "@palco.test",
                        "senha", "incorreta", "rememberMe", false)}) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(login)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mensagem").value("Email ou senha incorretos."));
        }
    }

    private Map<String, Object> cadastroBase(String tipoUsuario, LocalDate nascimento) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("nome", "Pessoa RF01 " + tipoUsuario);
        payload.put("dataNascimento", nascimento.toString());
        payload.put("telefone", "11999999999");
        payload.put("email", tipoUsuario.toLowerCase() + "-" + UUID.randomUUID() + "@palco.test");
        payload.put("senha", SENHA_VALIDA);
        payload.put("tipoUsuario", tipoUsuario);
        return payload;
    }

    private void cadastrar(Map<String, Object> payload) throws Exception {
        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());
    }
}
