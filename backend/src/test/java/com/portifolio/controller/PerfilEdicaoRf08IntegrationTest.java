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
import com.portifolio.model.Tag;
import com.portifolio.model.Usuario;
import com.portifolio.model.Vaga;
import com.portifolio.model.enums.ModeloTrabalho;
import com.portifolio.model.enums.StatusVaga;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.CandidaturaRepository;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.RefreshTokenRepository;
import com.portifolio.repository.TagRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.repository.VagaRepository;
import com.portifolio.security.JwtService;
import com.portifolio.service.RefreshTokenService;
import com.portifolio.validation.PasswordPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    @Autowired TagRepository tagRepository;
    @Autowired VagaRepository vagaRepository;
    @Autowired CandidaturaRepository candidaturaRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired RefreshTokenService refreshTokenService;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    @AfterEach
    void limparBanco() {
        jdbcTemplate.execute(
                "TRUNCATE candidaturas, vagas, tags, perfis_artistas, perfis_contratantes, refresh_tokens, usuarios RESTART IDENTITY CASCADE");
    }

    @Test
    void perfilArtistaExigeAutenticacaoPropriedadeETipoCorreto() throws Exception {
        PerfilArtista artista = novoArtista("artista-proprio-rf08@teste.com");
        PerfilArtista outro = novoArtista("artista-alheio-rf08@teste.com");
        PerfilContratante contratante = novoContratante("contratante-cruzado-rf08@teste.com");
        Tag tag = novaTag("Teatro");
        String corpo = json(perfilArtistaPayload(artista.getUsuarioId(), tag.getId()));

        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getUsuarioId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getUsuarioId())
                        .header("Authorization", bearer(outro.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/perfis-artistas/{id}", contratante.getUsuarioId())
                        .header("Authorization", bearer(contratante.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(perfilArtistaPayload(contratante.getUsuarioId(), tag.getId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void artistaCompletoComUmaTagAtualizaFlagETimestampSemDependerDeOpcionais() throws Exception {
        PerfilArtista artista = novoArtista("artista-completo-rf08@teste.com");
        artista.setUltimaAtualizacao(LocalDateTime.now().minusDays(2));
        perfilArtistaRepository.save(artista);
        LocalDateTime anterior = artista.getUltimaAtualizacao();
        Tag tag = novaTag("Música");

        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getUsuarioId())
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(perfilArtistaPayload(artista.getUsuarioId(), tag.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagIds.length()").value(1));

        Usuario atualizado = usuarioRepository.findById(artista.getUsuarioId()).orElseThrow();
        PerfilArtista perfil = perfilArtistaRepository.findById(artista.getUsuarioId()).orElseThrow();
        assertThat(atualizado.getPerfilCompleto()).isTrue();
        assertThat(perfil.getUltimaAtualizacao()).isAfter(anterior);
    }

    @Test
    void removerUltimaTagRebaixaPerfilCompletoParaFalse() throws Exception {
        PerfilArtista artista = novoArtista("artista-remove-tag@teste.com");
        Tag tag = novaTag("Dança");
        completarArtista(artista, tag);
        assertThat(usuarioRepository.findById(artista.getUsuarioId()).orElseThrow().getPerfilCompleto()).isTrue();

        Map<String, Object> semTags = perfilArtistaPayload(artista.getUsuarioId(), tag.getId());
        semTags.put("tagIds", List.of());
        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getUsuarioId())
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(semTags)))
                .andExpect(status().isOk());

        assertThat(usuarioRepository.findById(artista.getUsuarioId()).orElseThrow().getPerfilCompleto()).isFalse();
    }

    @Test
    void clienteNaoBurlaComPerfilCompletoEPerfilObrigatorioBlankContinuaFalse() throws Exception {
        PerfilArtista artista = novoArtista("artista-burla-rf08@teste.com");
        Tag tag = novaTag("Cinema");
        Map<String, Object> payload = perfilArtistaPayload(artista.getUsuarioId(), tag.getId());
        payload.put("biografia", "   ");
        payload.put("perfilCompleto", true);

        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getUsuarioId())
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isOk());

        assertThat(usuarioRepository.findById(artista.getUsuarioId()).orElseThrow().getPerfilCompleto()).isFalse();
    }

    @Test
    void tagInexistenteEhRejeitadaSemAtualizacaoParcial() throws Exception {
        PerfilArtista artista = novoArtista("artista-tag-inexistente@teste.com");

        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getUsuarioId())
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(perfilArtistaPayload(artista.getUsuarioId(), 999999L))))
                .andExpect(status().isNotFound());

        PerfilArtista persistido = perfilArtistaRepository.findById(artista.getUsuarioId()).orElseThrow();
        assertThat(persistido.getBiografia()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tags_artista where artista_id = ?", Integer.class,
                artista.getUsuarioId())).isZero();
        assertThat(usuarioRepository.findById(artista.getUsuarioId()).orElseThrow().getPerfilCompleto()).isFalse();
    }

    @Test
    void tagsDuplicadasGeramUmaUnicaAssociacao() throws Exception {
        PerfilArtista artista = novoArtista("artista-tags-duplicadas@teste.com");
        Tag tag = novaTag("Fotografia");
        Map<String, Object> payload = perfilArtistaPayload(artista.getUsuarioId(), tag.getId());
        payload.put("tagIds", List.of(tag.getId(), tag.getId()));

        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getUsuarioId())
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tags_artista where artista_id = ?", Integer.class, artista.getUsuarioId()))
                .isEqualTo(1);
    }

    @Test
    void medalhaEScoreEnviadosPeloArtistaSaoIgnorados() throws Exception {
        PerfilArtista artista = novoArtista("artista-score-rf08@teste.com");
        Tag tag = novaTag("Artes Visuais");
        Map<String, Object> payload = perfilArtistaPayload(artista.getUsuarioId(), tag.getId());
        payload.put("nivelMedalha", 5);
        payload.put("scoreEngajamento", 999.99);

        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getUsuarioId())
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isOk());

        PerfilArtista persistido = perfilArtistaRepository.findById(artista.getUsuarioId()).orElseThrow();
        assertThat(persistido.getNivelMedalha()).isEqualTo(1);
        assertThat(persistido.getScoreEngajamento()).isEqualByComparingTo("0.00");
    }

    @Test
    void contratanteEditaProprioPerfilENomeEmpresaEhOpcional() throws Exception {
        PerfilContratante contratante = novoContratante("contratante-completo-rf08@teste.com");

        mockMvc.perform(put("/api/perfis-contratantes/{id}", contratante.getUsuarioId())
                        .header("Authorization", bearer(contratante.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(perfilContratantePayload(contratante.getUsuarioId(), null))))
                .andExpect(status().isOk());
        assertThat(usuarioRepository.findById(contratante.getUsuarioId()).orElseThrow().getPerfilCompleto()).isTrue();

        Map<String, Object> empresaVazia = perfilContratantePayload(contratante.getUsuarioId(), "");
        mockMvc.perform(put("/api/perfis-contratantes/{id}", contratante.getUsuarioId())
                        .header("Authorization", bearer(contratante.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(empresaVazia)))
                .andExpect(status().isOk());
        assertThat(usuarioRepository.findById(contratante.getUsuarioId()).orElseThrow().getPerfilCompleto()).isTrue();
    }

    @Test
    void contratanteSemBiografiaOuLocalizacaoFicaIncompleto() throws Exception {
        PerfilContratante contratante = novoContratante("contratante-incompleto-rf08@teste.com");
        Map<String, Object> payload = perfilContratantePayload(contratante.getUsuarioId(), null);
        payload.put("biografia", null);
        mockMvc.perform(put("/api/perfis-contratantes/{id}", contratante.getUsuarioId())
                        .header("Authorization", bearer(contratante.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isOk());
        assertThat(usuarioRepository.findById(contratante.getUsuarioId()).orElseThrow().getPerfilCompleto()).isFalse();

        payload.put("biografia", "Biografia");
        payload.put("localizacao", "   ");
        mockMvc.perform(put("/api/perfis-contratantes/{id}", contratante.getUsuarioId())
                        .header("Authorization", bearer(contratante.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isOk());
        assertThat(usuarioRepository.findById(contratante.getUsuarioId()).orElseThrow().getPerfilCompleto()).isFalse();
    }

    @Test
    void contratanteNaoEditaPerfilAlheioNemPerfilDeArtista() throws Exception {
        PerfilContratante dono = novoContratante("contratante-dono-rf08@teste.com");
        PerfilContratante outro = novoContratante("contratante-outro-rf08@teste.com");
        PerfilArtista artista = novoArtista("artista-cruzado-contratante@teste.com");

        mockMvc.perform(put("/api/perfis-contratantes/{id}", dono.getUsuarioId())
                        .header("Authorization", bearer(outro.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(perfilContratantePayload(dono.getUsuarioId(), null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/perfis-contratantes/{id}", artista.getUsuarioId())
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(perfilContratantePayload(artista.getUsuarioId(), null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void edicaoDeUsuarioAtualizaSomenteNomeTelefoneEmailERecalcula() throws Exception {
        PerfilContratante contratante = novoContratante("usuario-edicao-rf08@teste.com");
        completarContratante(contratante, null);
        LocalDate nascimentoOriginal = contratante.getUsuario().getDataNascimento();

        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(contratante.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Nome Atualizado",
                                "dataNascimento", "2000-12-31",
                                "telefone", "11888888888",
                                "email", "usuario-editado-rf08@teste.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nome Atualizado"))
                .andExpect(jsonPath("$.telefone").value("11888888888"))
                .andExpect(jsonPath("$.email").value("usuario-editado-rf08@teste.com"))
                .andExpect(jsonPath("$.perfilCompleto").value(true));

        Usuario persistido = usuarioRepository.findById(contratante.getUsuarioId()).orElseThrow();
        assertThat(persistido.getDataNascimento()).isEqualTo(nascimentoOriginal);
    }

    @Test
    void menorAtualizaDadosDoProprioResponsavelSemAlterarPerfilCompleto() throws Exception {
        PerfilContratante menor = novoContratanteMenor("menor-responsavel-rf08@teste.com");
        completarContratante(menor, null);
        assertThat(usuarioRepository.findById(menor.getUsuarioId()).orElseThrow().getPerfilCompleto()).isTrue();

        Map<String, Object> payload = dadosUsuario(menor.getUsuario());
        payload.put("nomeResponsavel", "Responsável Atualizado");
        payload.put("telefoneResponsavel", "11888887777");
        payload.put("emailResponsavel", "responsavel-atualizado@teste.com");

        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(menor.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeResponsavel").value("Responsável Atualizado"))
                .andExpect(jsonPath("$.telefoneResponsavel").value("11888887777"))
                .andExpect(jsonPath("$.emailResponsavel").value("responsavel-atualizado@teste.com"))
                .andExpect(jsonPath("$.perfilCompleto").value(true));

        Usuario persistido = usuarioRepository.findById(menor.getUsuarioId()).orElseThrow();
        assertThat(persistido.getNomeResponsavel()).isEqualTo("Responsável Atualizado");
        assertThat(persistido.getTelefoneResponsavel()).isEqualTo("11888887777");
        assertThat(persistido.getEmailResponsavel()).isEqualTo("responsavel-atualizado@teste.com");
        assertThat(persistido.getPerfilCompleto()).isTrue();
    }

    @Test
    void menorNaoPodeDeixarCamposObrigatoriosDoResponsavelEmBranco() throws Exception {
        PerfilArtista menor = novoArtistaMenor("menor-responsavel-blank-rf08@teste.com");

        for (String campo : List.of("nomeResponsavel", "telefoneResponsavel", "emailResponsavel")) {
            Map<String, Object> payload = dadosUsuario(menor.getUsuario());
            payload.put(campo, "   ");
            mockMvc.perform(put("/api/usuarios/me")
                            .header("Authorization", bearer(menor.getUsuario()))
                            .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                    .andExpect(campo.equals("emailResponsavel")
                            ? status().isBadRequest()
                            : status().isUnprocessableEntity());
        }

        Usuario persistido = usuarioRepository.findById(menor.getUsuarioId()).orElseThrow();
        assertThat(persistido.getNomeResponsavel()).isEqualTo("Responsável Original");
        assertThat(persistido.getTelefoneResponsavel()).isEqualTo("11911112222");
        assertThat(persistido.getEmailResponsavel()).isEqualTo("responsavel-original@teste.com");
    }

    @Test
    void emailInvalidoDoResponsavelEhRejeitadoNoServidor() throws Exception {
        PerfilArtista menor = novoArtistaMenor("menor-responsavel-email-rf08@teste.com");
        Map<String, Object> payload = dadosUsuario(menor.getUsuario());
        payload.put("emailResponsavel", "email-invalido");

        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(menor.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes[0]").value(
                        "emailResponsavel: E-mail do responsável inválido"));
    }

    @Test
    void dadosDoResponsavelExigemAutenticacaoEPropriedade() throws Exception {
        PerfilArtista menor = novoArtistaMenor("menor-responsavel-dono-rf08@teste.com");
        PerfilArtista outro = novoArtista("menor-responsavel-invasor-rf08@teste.com");
        Map<String, Object> payloadMe = dadosUsuario(menor.getUsuario());
        payloadMe.put("nomeResponsavel", "Tentativa sem token");

        mockMvc.perform(put("/api/usuarios/me")
                        .contentType(MediaType.APPLICATION_JSON).content(json(payloadMe)))
                .andExpect(status().isUnauthorized());

        Map<String, Object> payloadLegado = new java.util.LinkedHashMap<>();
        payloadLegado.put("nome", menor.getUsuario().getNome());
        payloadLegado.put("dataNascimento", menor.getUsuario().getDataNascimento().toString());
        payloadLegado.put("telefone", menor.getUsuario().getTelefone());
        payloadLegado.put("email", menor.getUsuario().getEmail());
        payloadLegado.put("tipoUsuario", menor.getUsuario().getTipoUsuario().name());
        payloadLegado.put("nomeResponsavel", "Tentativa alheia");
        payloadLegado.put("telefoneResponsavel", "11777776666");
        payloadLegado.put("emailResponsavel", "tentativa-alheia@teste.com");

        mockMvc.perform(put("/api/usuarios/{id}", menor.getUsuarioId())
                        .header("Authorization", bearer(outro.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payloadLegado)))
                .andExpect(status().isForbidden());

        assertThat(usuarioRepository.findById(menor.getUsuarioId()).orElseThrow().getNomeResponsavel())
                .isEqualTo("Responsável Original");
    }

    @Test
    void adultoNaoPassaAExigirResponsavelNemPerdeDadosExistentes() throws Exception {
        PerfilContratante adulto = novoContratante("adulto-responsavel-rf08@teste.com");
        Map<String, Object> semResponsavel = dadosUsuario(adulto.getUsuario());

        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(adulto.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(semResponsavel)))
                .andExpect(status().isOk());

        adulto.getUsuario().setNomeResponsavel("Dado legado");
        adulto.getUsuario().setTelefoneResponsavel("11666665555");
        adulto.getUsuario().setEmailResponsavel("legado@teste.com");
        usuarioRepository.save(adulto.getUsuario());

        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(adulto.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(semResponsavel)))
                .andExpect(status().isOk());

        Usuario persistido = usuarioRepository.findById(adulto.getUsuarioId()).orElseThrow();
        assertThat(persistido.getNomeResponsavel()).isEqualTo("Dado legado");
        assertThat(persistido.getTelefoneResponsavel()).isEqualTo("11666665555");
        assertThat(persistido.getEmailResponsavel()).isEqualTo("legado@teste.com");
    }

    @Test
    void emailDuplicadoELimitesInvalidosSaoBloqueados() throws Exception {
        PerfilArtista usuario = novoArtista("usuario-validacao-rf08@teste.com");
        novoArtista("email-existente-rf08@teste.com");

        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(usuario.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Nome", "telefone", "11999999999",
                                "email", "email-existente-rf08@teste.com"))))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(usuario.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "x".repeat(151), "telefone", "1".repeat(21),
                                "email", "invalido"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes.length()").value(3));
    }

    @Test
    void rotaLegadaPorIdBloqueiaIdorEManipulacaoDeCamposProtegidos() throws Exception {
        PerfilArtista dono = novoArtista("usuario-legado-dono@teste.com");
        PerfilArtista outro = novoArtista("usuario-legado-outro@teste.com");
        Map<String, Object> payload = Map.of(
                "nome", "Ataque", "dataNascimento", "2001-01-01", "telefone", "11999999999",
                "email", dono.getUsuario().getEmail(), "senha", "nova-sem-atual",
                "tipoUsuario", "CONTRATANTE", "perfilCompleto", true);

        mockMvc.perform(put("/api/usuarios/{id}", dono.getUsuarioId())
                        .header("Authorization", bearer(outro.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/usuarios/{id}", dono.getUsuarioId())
                        .header("Authorization", bearer(dono.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isUnprocessableEntity());

        Usuario persistido = usuarioRepository.findById(dono.getUsuarioId()).orElseThrow();
        assertThat(persistido.getTipoUsuario()).isEqualTo(TipoUsuario.ARTISTA);
        assertThat(persistido.getPerfilCompleto()).isFalse();
    }

    @Test
    void consultaGenericaDeOutroUsuarioEhBloqueada() throws Exception {
        PerfilArtista consultado = novoArtista("privado-consultado-rf08@teste.com");
        PerfilContratante consulente = novoContratante("privado-consulente-rf08@teste.com");
        consultado.getUsuario().setNomeResponsavel("Responsável privado");
        consultado.getUsuario().setTelefoneResponsavel("11555554444");
        consultado.getUsuario().setEmailResponsavel("responsavel-privado@teste.com");
        usuarioRepository.save(consultado.getUsuario());

        mockMvc.perform(get("/api/usuarios/{id}", consultado.getUsuarioId())
                        .header("Authorization", bearer(consulente.getUsuario())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefone").doesNotExist())
                .andExpect(jsonPath("$.dataNascimento").doesNotExist())
                .andExpect(jsonPath("$.nomeResponsavel").doesNotExist())
                .andExpect(jsonPath("$.telefoneResponsavel").doesNotExist())
                .andExpect(jsonPath("$.emailResponsavel").doesNotExist());
    }

    @Test
    void trocaDeSenhaExigeSenhaAtual() throws Exception {
        PerfilArtista artista = novoArtista("senha-exigida-rf08@teste.com");
        Map<String, Object> base = dadosUsuario(artista.getUsuario());
        base.put("novaSenha", "NovaSenha123!");

        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(base)))
                .andExpect(status().isUnprocessableEntity());
        base.put("senhaAtual", "senha-incorreta");
        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(base)))
                .andExpect(status().isForbidden());
    }

    @Test
    void trocaDeSenhaUsaBCryptAlteraLoginERevogaRefreshTokens() throws Exception {
        PerfilArtista artista = novoArtista("senha-sucesso-rf08@teste.com");
        String refreshToken = refreshTokenService.gerarRefreshToken(artista.getUsuario());
        Map<String, Object> payload = dadosUsuario(artista.getUsuario());
        payload.put("senhaAtual", "SenhaAtual123!");
        payload.put("novaSenha", "NovaSenha456!");

        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isOk());

        Usuario persistido = usuarioRepository.findById(artista.getUsuarioId()).orElseThrow();
        assertThat(persistido.getSenha()).isNotEqualTo("NovaSenha456!");
        assertThat(passwordEncoder.matches("NovaSenha456!", persistido.getSenha())).isTrue();
        assertThat(refreshTokenRepository.findAll()).allMatch(token -> !token.getAtivo());

        login(artista.getUsuario().getEmail(), "SenhaAtual123!").andExpect(status().isUnauthorized());
        login(artista.getUsuario().getEmail(), "NovaSenha456!").andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isNotFound());
    }

    @Test
    void senhaNovaInvalidaNaoAlteraDadosHashNemRevogaSessoes() throws Exception {
        PerfilArtista artista = novoArtista("senha-invalida-rf08@teste.com");
        String hashAnterior = artista.getUsuario().getSenha();
        refreshTokenService.gerarRefreshToken(artista.getUsuario());
        Map<String, Object> payload = dadosUsuario(artista.getUsuario());
        payload.put("nome", "Nome que deve sofrer rollback");
        payload.put("senhaAtual", "SenhaAtual123!");
        payload.put("novaSenha", "artista123");

        mockMvc.perform(put("/api/usuarios/me")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(PasswordPolicy.MESSAGE));

        Usuario persistido = usuarioRepository.findById(artista.getUsuarioId()).orElseThrow();
        assertThat(persistido.getNome()).isEqualTo("Usuário RF08");
        assertThat(persistido.getSenha()).isEqualTo(hashAnterior);
        assertThat(refreshTokenRepository.findAll()).allMatch(token -> token.getAtivo());
    }

    @Test
    void postUsuariosEhBloqueadoAntesDaValidacaoDeSenha() throws Exception {
        PerfilArtista autenticado = novoArtista("criador-rf08@teste.com");
        String emailNovo = "bypass-politica-rf08@teste.com";
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("nome", "Tentativa de bypass");
        payload.put("dataNascimento", "1990-01-01");
        payload.put("telefone", "11999999999");
        payload.put("email", emailNovo);
        payload.put("senha", "artista123");
        payload.put("tipoUsuario", "ARTISTA");

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", bearer(autenticado.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isForbidden());

        assertThat(usuarioRepository.findByEmail(emailNovo)).isEmpty();
    }

    @Test
    void contaExclusivamenteGoogleNaoGanhaSenhaLocal() throws Exception {
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
        assertThat(usuarioRepository.findById(artista.getUsuarioId()).orElseThrow().getSenha()).isNull();
    }

    @Test
    void rf06RefleteImediatamentePromocaoERebaixamentoDoPerfil() throws Exception {
        PerfilContratante contratante = novoContratante("rf06-contratante-rf08@teste.com");
        Vaga vaga1 = novaVaga(contratante, "Vaga 1");
        Vaga vaga2 = novaVaga(contratante, "Vaga 2");
        PerfilArtista artista = novoArtista("rf06-artista-rf08@teste.com");
        String candidatura1 = candidaturaPayload(vaga1.getId(), artista.getUsuarioId());

        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(candidatura1))
                .andExpect(status().isUnprocessableEntity());

        Tag tag = novaTag("Produção");
        completarArtista(artista, tag);
        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(candidatura1))
                .andExpect(status().isCreated());

        Map<String, Object> semTags = perfilArtistaPayload(artista.getUsuarioId(), tag.getId());
        semTags.put("tagIds", List.of());
        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getUsuarioId())
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(semTags)))
                .andExpect(status().isOk());
        assertThat(usuarioRepository.findById(artista.getUsuarioId()).orElseThrow().getPerfilCompleto()).isFalse();

        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidaturaPayload(vaga2.getId(), artista.getUsuarioId())))
                .andExpect(status().isUnprocessableEntity());
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

    private PerfilArtista novoArtistaMenor(String email) {
        PerfilArtista perfil = novoArtista(email);
        prepararUsuarioMenor(perfil.getUsuario());
        return perfil;
    }

    private PerfilContratante novoContratanteMenor(String email) {
        PerfilContratante perfil = novoContratante(email);
        prepararUsuarioMenor(perfil.getUsuario());
        return perfil;
    }

    private void prepararUsuarioMenor(Usuario usuario) {
        usuario.setDataNascimento(LocalDate.now().minusYears(16));
        usuario.setNomeResponsavel("Responsável Original");
        usuario.setTelefoneResponsavel("11911112222");
        usuario.setEmailResponsavel("responsavel-original@teste.com");
        usuarioRepository.save(usuario);
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

    private Tag novaTag(String nome) {
        Tag tag = new Tag();
        tag.setNome(nome);
        return tagRepository.save(tag);
    }

    private void completarArtista(PerfilArtista artista, Tag tag) throws Exception {
        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getUsuarioId())
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(perfilArtistaPayload(artista.getUsuarioId(), tag.getId()))))
                .andExpect(status().isOk());
    }

    private void completarContratante(PerfilContratante contratante, String nomeEmpresa) throws Exception {
        mockMvc.perform(put("/api/perfis-contratantes/{id}", contratante.getUsuarioId())
                        .header("Authorization", bearer(contratante.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(perfilContratantePayload(contratante.getUsuarioId(), nomeEmpresa))))
                .andExpect(status().isOk());
    }

    private Map<String, Object> perfilArtistaPayload(Long usuarioId, Long tagId) {
        return new java.util.LinkedHashMap<>(Map.of(
                "usuarioId", usuarioId,
                "biografia", "Biografia completa",
                "localizacao", "São Paulo, SP",
                "urlPortfolio", "https://portfolio.example",
                "tagIds", List.of(tagId)));
    }

    private Map<String, Object> perfilContratantePayload(Long usuarioId, String nomeEmpresa) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("usuarioId", usuarioId);
        payload.put("nomeEmpresa", nomeEmpresa);
        payload.put("tipoPerfil", null);
        payload.put("biografia", "Biografia completa");
        payload.put("localizacao", "São Paulo, SP");
        payload.put("bannerUrl", null);
        return payload;
    }

    private Map<String, Object> dadosUsuario(Usuario usuario) {
        return new java.util.LinkedHashMap<>(Map.of(
                "nome", usuario.getNome(),
                "telefone", usuario.getTelefone(),
                "email", usuario.getEmail()));
    }

    private Vaga novaVaga(PerfilContratante contratante, String titulo) {
        Vaga vaga = new Vaga();
        vaga.setContratante(contratante);
        vaga.setTitulo(titulo);
        vaga.setDescricao("Descrição");
        vaga.setRequisitos("Requisitos");
        vaga.setRemuneraValor(new BigDecimal("1000.00"));
        vaga.setFormaPagamento("Pix");
        vaga.setCidade("São Paulo");
        vaga.setEstado("SP");
        vaga.setModeloTrabalho(ModeloTrabalho.REMOTO);
        vaga.setTipoContrato("Freelance");
        vaga.setStatus(StatusVaga.ABERTA);
        vaga.setDataPublicacao(LocalDateTime.now());
        vaga.setTags(new HashSet<>());
        return vagaRepository.save(vaga);
    }

    private String candidaturaPayload(Long vagaId, Long artistaId) throws Exception {
        return json(Map.of(
                "vagaId", vagaId,
                "artistaId", artistaId,
                "mensagemApresentacao", "Tenho interesse",
                "linkPortfolioCandidatura", "https://portfolio.example"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String senha) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "senha", senha, "rememberMe", false))));
    }

    private String json(Object valor) throws Exception {
        return objectMapper.writeValueAsString(valor);
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.gerarToken(usuario);
    }
}
