package com.portifolio.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portifolio.model.Candidatura;
import com.portifolio.model.LogVagaCancelada;
import com.portifolio.model.Notificacao;
import com.portifolio.dto.NotificacaoResponse;
import com.portifolio.realtime.NotificacaoSseService;
import com.portifolio.repository.LogVagaCanceladaRepository;
import com.portifolio.repository.NotificacaoRepository;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Usuario;
import com.portifolio.model.Vaga;
import com.portifolio.model.enums.ModeloTrabalho;
import com.portifolio.model.enums.StatusCandidatura;
import com.portifolio.model.enums.StatusVaga;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.CandidaturaRepository;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.repository.VagaRepository;
import com.portifolio.security.JwtService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class VagaCancelamentoRf25IntegrationTest {

    private static final String MOTIVO_PADRAO = "Encerramento do projeto artístico.";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScript("db/schema-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PerfilContratanteRepository perfilContratanteRepository;
    @Autowired PerfilArtistaRepository perfilArtistaRepository;
    @Autowired VagaRepository vagaRepository;
    @Autowired CandidaturaRepository candidaturaRepository;
    @Autowired JwtService jwtService;
    @Autowired DataSource dataSource;
    @MockitoSpyBean LogVagaCanceladaRepository logRepository;
    @MockitoSpyBean NotificacaoRepository notificacaoRepository;
    @MockitoSpyBean SimpMessagingTemplate messagingTemplate;
    @MockitoSpyBean NotificacaoSseService sseService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void limparBanco() {
        jdbcTemplate.execute("TRUNCATE log_vagas_canceladas, candidaturas, vagas, "
                + "perfis_artistas, perfis_contratantes, usuarios RESTART IDENTITY CASCADE");
    }

    @Test
    void visitanteNaoPodeCancelar() throws Exception {
        Vaga vaga = novaVaga(novoContratante("visitante@rf25.test"), StatusVaga.ABERTA);

        mockMvc.perform(delete("/api/vagas/{id}", vaga.getId()))
                .andExpect(status().isUnauthorized());

        assertThat(statusPersistido(vaga)).isEqualTo(StatusVaga.ABERTA);
    }

    @Test
    void corpoAusenteNaoPodeCancelar() throws Exception {
        PerfilContratante dono = novoContratante("corpo-ausente@rf25.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        Candidatura candidatura = novaCandidatura(
                vaga, novoArtista("candidato-corpo-ausente@rf25.test"), StatusCandidatura.EM_ANALISE);

        mockMvc.perform(delete("/api/vagas/{id}", vaga.getId())
                        .header("Authorization", bearer(dono.getUsuario())))
                .andExpect(status().isBadRequest());

        assertThat(statusPersistido(vaga)).isEqualTo(StatusVaga.ABERTA);
        assertThat(candidaturaRepository.findById(candidatura.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusCandidatura.EM_ANALISE);
        assertThat(quantidadeLogs(vaga)).isZero();
    }

    @ParameterizedTest
    @MethodSource("requisicoesInvalidas")
    void confirmacaoOuMotivoInvalidosNaoPodemCancelar(String corpo) throws Exception {
        PerfilContratante dono = novoContratante("request-invalido@rf25.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        Candidatura candidatura = novaCandidatura(
                vaga, novoArtista("candidato-request-invalido@rf25.test"), StatusCandidatura.PENDENTE);

        cancelarComCorpo(vaga, dono.getUsuario(), corpo)
                .andExpect(status().isBadRequest());

        assertThat(statusPersistido(vaga)).isEqualTo(StatusVaga.ABERTA);
        assertThat(candidaturaRepository.findById(candidatura.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusCandidatura.PENDENTE);
        assertThat(quantidadeLogs(vaga)).isZero();
    }

    @Test
    void artistaNaoPodeCancelar() throws Exception {
        Vaga vaga = novaVaga(novoContratante("dono-artista@rf25.test"), StatusVaga.ABERTA);
        PerfilArtista artista = novoArtista("artista@rf25.test");

        cancelar(vaga, artista.getUsuario())
                .andExpect(status().isForbidden());

        assertThat(statusPersistido(vaga)).isEqualTo(StatusVaga.ABERTA);
    }

    @Test
    void outroContratanteNaoPodeCancelarNemAlterarCandidaturas() throws Exception {
        PerfilContratante dono = novoContratante("dono-idor@rf25.test");
        PerfilContratante invasor = novoContratante("invasor-idor@rf25.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        Candidatura candidatura = novaCandidatura(
                vaga, novoArtista("candidato-idor@rf25.test"), StatusCandidatura.EM_ANALISE);

        cancelar(vaga, invasor.getUsuario())
                .andExpect(status().isForbidden());

        assertThat(statusPersistido(vaga)).isEqualTo(StatusVaga.ABERTA);
        assertThat(candidaturaRepository.findById(candidatura.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusCandidatura.EM_ANALISE);
        assertThat(quantidadeLogs(vaga)).isZero();
    }

    @Test
    void vagaInexistenteDeveRetornar404() throws Exception {
        PerfilContratante contratante = novoContratante("inexistente@rf25.test");

        mockMvc.perform(delete("/api/vagas/{id}", 999999)
                        .header("Authorization", bearer(contratante.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCancelamento(MOTIVO_PADRAO)))
                .andExpect(status().isNotFound());
    }

    @Test
    void vagaAbertaDeveSerCanceladaPorSoftDeleteERegistrarLog() throws Exception {
        PerfilContratante dono = novoContratante("aberta@rf25.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);

        cancelar(vaga, dono.getUsuario())
                .andExpect(status().isNoContent());

        LocalDateTime depois = LocalDateTime.now().plusSeconds(1);
        Vaga persistida = vagaRepository.findById(vaga.getId()).orElseThrow();
        assertThat(persistida.getId()).isEqualTo(vaga.getId());
        assertThat(persistida.getStatus()).isEqualTo(StatusVaga.CANCELADA);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from vagas where id = ?", Integer.class, vaga.getId())).isEqualTo(1);

        Map<String, Object> log = jdbcTemplate.queryForMap(
                "select vaga_id, cancelado_por_id, data_cancelamento, motivo "
                        + "from log_vagas_canceladas where vaga_id = ?", vaga.getId());
        assertThat(((Number) log.get("vaga_id")).longValue()).isEqualTo(vaga.getId());
        assertThat(((Number) log.get("cancelado_por_id")).longValue())
                .isEqualTo(dono.getUsuarioId());
        assertThat(((Timestamp) log.get("data_cancelamento")).toLocalDateTime())
                .isBetween(antes, depois);
        assertThat(log.get("motivo")).isEqualTo(MOTIVO_PADRAO);
    }

    @Test
    void vagaPausadaTambemDeveSerCancelada() throws Exception {
        PerfilContratante dono = novoContratante("pausada@rf25.test");
        Vaga vaga = novaVaga(dono, StatusVaga.PAUSADA);

        cancelar(vaga, dono.getUsuario())
                .andExpect(status().isNoContent());

        assertThat(statusPersistido(vaga)).isEqualTo(StatusVaga.CANCELADA);
        assertThat(quantidadeLogs(vaga)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = StatusVaga.class, names = {"ENCERRADA", "CANCELADA"})
    void estadoFinalNaoPodeSerCancelado(StatusVaga estado) throws Exception {
        PerfilContratante dono = novoContratante("final-" + estado + "@rf25.test");
        Vaga vaga = novaVaga(dono, estado);

        cancelar(vaga, dono.getUsuario())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(
                        org.hamcrest.Matchers.containsString(estado.name())));

        assertThat(statusPersistido(vaga)).isEqualTo(estado);
        assertThat(quantidadeLogs(vaga)).isZero();
    }

    @Test
    void todosOsStatusDeCandidaturaDevemVirarCanceladaPorVagaSemPerderHistorico()
            throws Exception {
        PerfilContratante dono = novoContratante("status-candidaturas@rf25.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        Map<Long, Long> vagasOriginais = new LinkedHashMap<>();
        int indice = 0;
        for (StatusCandidatura statusInicial : StatusCandidatura.values()) {
            Candidatura candidatura = novaCandidatura(
                    vaga,
                    novoArtista("status-" + indice++ + "@rf25.test"),
                    statusInicial);
            vagasOriginais.put(candidatura.getId(), vaga.getId());
        }

        cancelar(vaga, dono.getUsuario()).andExpect(status().isNoContent());

        var persistidas = candidaturaRepository.findByVagaId(vaga.getId());
        assertThat(persistidas).hasSize(StatusCandidatura.values().length);
        assertThat(persistidas).allSatisfy(candidatura -> {
            assertThat(candidatura.getId()).isIn(vagasOriginais.keySet());
            assertThat(candidatura.getVaga().getId()).isEqualTo(vagasOriginais.get(candidatura.getId()));
            assertThat(candidatura.getStatus()).isEqualTo(StatusCandidatura.CANCELADA_POR_VAGA);
        });
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from candidaturas where vaga_id = ?",
                Integer.class, vaga.getId())).isEqualTo(StatusCandidatura.values().length);
        assertThat(quantidadeLogs(vaga)).isOne();
        assertThat(notificacaoRepository.findAll()).hasSize(StatusCandidatura.values().length)
                .allSatisfy(notificacao -> {
                    assertThat(notificacao.getLida()).isFalse();
                    assertThat(notificacao.getMensagem()).contains("cancelada");
                    assertThat(notificacao.getLink()).isEqualTo("detalhe-vaga.html?id=" + vaga.getId());
                });
        assertThat(jdbcTemplate.queryForObject(
                "select count(distinct usuario_destino_id) from notificacoes", Integer.class))
                .isEqualTo(StatusCandidatura.values().length);
    }

    @Test
    void falhaAoRegistrarLogDeveFazerRollbackCompletoNoPostgresql() throws Exception {
        PerfilContratante dono = novoContratante("rollback@rf25.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        Candidatura candidatura = novaCandidatura(
                vaga, novoArtista("candidato-rollback@rf25.test"), StatusCandidatura.PENDENTE);
        doAnswer(invocation -> {
            logRepository.saveAndFlush(invocation.<LogVagaCancelada>getArgument(0));
            throw new IllegalStateException("Falha simulada na persistência do log");
        }).when(logRepository).save(any(LogVagaCancelada.class));

        assertThatThrownBy(() -> cancelar(vaga, dono.getUsuario()))
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(statusPersistido(vaga)).isEqualTo(StatusVaga.ABERTA);
        assertThat(candidaturaRepository.findById(candidatura.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusCandidatura.PENDENTE);
        assertThat(quantidadeLogs(vaga)).isZero();
        assertThat(notificacaoRepository.count()).isZero();
        verificarSemEntrega();
    }

    @ParameterizedTest
    @EnumSource(value = StatusVaga.class, names = {"ABERTA", "PAUSADA"})
    void falhaNaSegundaNotificacaoDesfazTodoCancelamento(StatusVaga estado) throws Exception {
        PerfilContratante dono = novoContratante("dono-atomicidade@rf28.test");
        Vaga vaga = novaVaga(dono, estado);
        Candidatura primeira = novaCandidatura(vaga, novoArtista("primeiro@rf28.test"),
                StatusCandidatura.PENDENTE);
        Candidatura segunda = novaCandidatura(vaga, novoArtista("segundo@rf28.test"),
                StatusCandidatura.EM_ANALISE);
        doAnswer(invocation -> {
            List<Notificacao> notificacoes = invocation.getArgument(0);
            assertThat(notificacoes).hasSize(2);
            notificacaoRepository.saveAndFlush(notificacoes.getFirst());
            assertThat(notificacaoRepository.count()).isOne();
            assertThat(quantidadeLogs(vaga)).isOne();
            throw new IllegalStateException("Falha simulada ao persistir a segunda notificação");
        }).when(notificacaoRepository).saveAllAndFlush(any());

        assertThatThrownBy(() -> cancelar(vaga, dono.getUsuario()))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(statusPersistido(vaga)).isEqualTo(estado);
        assertThat(candidaturaRepository.findById(primeira.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusCandidatura.PENDENTE);
        assertThat(candidaturaRepository.findById(segunda.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusCandidatura.EM_ANALISE);
        assertThat(quantidadeLogs(vaga)).isZero();
        assertThat(notificacaoRepository.count()).isZero();
        verificarSemEntrega();
    }

    @ParameterizedTest
    @ValueSource(strings = {"STOMP", "SSE"})
    void falhaDeTransporteAposCommitPreservaCancelamentoENotificacao(String transporte) throws Exception {
        PerfilContratante dono = novoContratante("dono-transporte@rf28.test");
        PerfilArtista artista = novoArtista("destino-transporte@rf28.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        novaCandidatura(vaga, artista, StatusCandidatura.PENDENTE);
        if ("STOMP".equals(transporte)) {
            doAnswer(invocation -> {
                verificarCommitPorConexaoIndependente(vaga);
                throw new IllegalStateException("STOMP indisponível");
            }).when(messagingTemplate).convertAndSendToUser(
                    anyString(), anyString(), any(NotificacaoResponse.class));
        } else {
            doAnswer(invocation -> {
                verificarCommitPorConexaoIndependente(vaga);
                throw new IllegalStateException("SSE indisponível");
            }).when(sseService).entregar(anyLong(), any(NotificacaoResponse.class));
        }

        cancelar(vaga, dono.getUsuario()).andExpect(status().isNoContent());

        verificarCommitPorConexaoIndependente(vaga);
        verificarNotificacaoRecuperavel(artista);
        verify(messagingTemplate).convertAndSendToUser(
                anyString(), anyString(), any(NotificacaoResponse.class));
        verify(sseService).entregar(anyLong(), any(NotificacaoResponse.class));
    }

    @Test
    void candidatoOfflineRecuperaNotificacaoPersistidaDepois() throws Exception {
        PerfilContratante dono = novoContratante("dono-offline@rf28.test");
        PerfilArtista artista = novoArtista("offline@rf28.test");
        Vaga vaga = novaVaga(dono, StatusVaga.PAUSADA);
        novaCandidatura(vaga, artista, StatusCandidatura.EM_ANALISE);

        cancelar(vaga, dono.getUsuario()).andExpect(status().isNoContent());

        verificarCommitPorConexaoIndependente(vaga);
        verificarNotificacaoRecuperavel(artista);
    }

    private void verificarNotificacaoRecuperavel(PerfilArtista artista) throws Exception {
        mockMvc.perform(get("/api/notificacoes")
                        .header("Authorization", bearer(artista.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].lida").value(false))
                .andExpect(jsonPath("$.content[0].mensagem").value(
                        org.hamcrest.Matchers.containsString("cancelada")));
    }

    private void verificarSemEntrega() {
        verify(messagingTemplate, never()).convertAndSendToUser(
                anyString(), anyString(), any(NotificacaoResponse.class));
        verify(sseService, never()).entregar(anyLong(), any(NotificacaoResponse.class));
    }

    private void verificarCommitPorConexaoIndependente(Vaga vaga) throws Exception {
        // Outra conexão só enxerga os quatro registros depois do commit real no PostgreSQL.
        try (var conexao = dataSource.getConnection();
                var consulta = conexao.prepareStatement("""
                        select v.status,
                               (select count(*) from candidaturas c where c.vaga_id = v.id
                                    and c.status = ?),
                               (select count(*) from log_vagas_canceladas l where l.vaga_id = v.id),
                               (select count(*) from notificacoes n where n.lida = false)
                        from vagas v where v.id = ?
                        """)) {
            consulta.setString(1, StatusCandidatura.CANCELADA_POR_VAGA.getDatabaseValue());
            consulta.setLong(2, vaga.getId());
            try (var resultado = consulta.executeQuery()) {
                assertThat(resultado.next()).isTrue();
                assertThat(resultado.getString(1)).isEqualTo(StatusVaga.CANCELADA.getDatabaseValue());
                assertThat(resultado.getInt(2)).isOne();
                assertThat(resultado.getInt(3)).isOne();
                assertThat(resultado.getInt(4)).isOne();
            }
        }
    }

    @Test
    void rf03DeveOcultarDoFeedEPreservarHistoricoSomenteDoCandidato() throws Exception {
        PerfilContratante dono = novoContratante("feed@rf25.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        PerfilArtista candidato = novoArtista("candidato-feed@rf25.test");
        PerfilArtista terceiro = novoArtista("terceiro-feed@rf25.test");
        novaCandidatura(vaga, candidato, StatusCandidatura.PENDENTE);

        cancelar(vaga, dono.getUsuario()).andExpect(status().isNoContent());

        JsonNode publico = listarVagas(null);
        assertThat(ids(publico.get("content"))).doesNotContain(vaga.getId());
        assertThat(ids(publico.get("vagasCanceladasComCandidatura"))).isEmpty();

        JsonNode doCandidato = listarVagas(candidato.getUsuario());
        assertThat(ids(doCandidato.get("content"))).doesNotContain(vaga.getId());
        assertThat(ids(doCandidato.get("vagasCanceladasComCandidatura"))).contains(vaga.getId());

        JsonNode doTerceiro = listarVagas(terceiro.getUsuario());
        assertThat(ids(doTerceiro.get("vagasCanceladasComCandidatura")))
                .doesNotContain(vaga.getId());
    }

    @Test
    void rf05DevePreservarDetalhesParaDonoECandidatoEOcultarDeTerceiro() throws Exception {
        PerfilContratante dono = novoContratante("detalhes@rf25.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        PerfilArtista candidato = novoArtista("candidato-detalhes@rf25.test");
        PerfilArtista terceiro = novoArtista("terceiro-detalhes@rf25.test");
        novaCandidatura(vaga, candidato, StatusCandidatura.EM_ANALISE);

        cancelar(vaga, dono.getUsuario()).andExpect(status().isNoContent());

        detalhar(vaga, dono.getUsuario())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
        detalhar(vaga, candidato.getUsuario())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusMinhaCandidatura").value("CANCELADA_POR_VAGA"));
        detalhar(vaga, terceiro.getUsuario())
                .andExpect(status().isNotFound());
    }

    @Test
    void rf06NaoDeveAceitarNovaCandidaturaEmVagaCancelada() throws Exception {
        PerfilContratante dono = novoContratante("rf06@rf25.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        PerfilArtista artista = novoArtista("novo-candidato@rf25.test");

        cancelar(vaga, dono.getUsuario()).andExpect(status().isNoContent());

        candidatar(vaga, artista).andExpect(status().isUnprocessableEntity());
        assertThat(candidaturaRepository.findByVagaId(vaga.getId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDER", "REABRIR", "ENCERRAR"})
    void rf31NaoDeveAlterarVagaCancelada(String acao) throws Exception {
        PerfilContratante dono = novoContratante("rf31-" + acao + "@rf25.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        cancelar(vaga, dono.getUsuario()).andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/vagas/{id}/status", vaga.getId())
                        .header("Authorization", bearer(dono.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("acao", acao))))
                .andExpect(status().isUnprocessableEntity());

        assertThat(statusPersistido(vaga)).isEqualTo(StatusVaga.CANCELADA);
    }

    private ResultActions cancelar(Vaga vaga, Usuario usuario) throws Exception {
        return cancelarComCorpo(vaga, usuario, corpoCancelamento("  " + MOTIVO_PADRAO + "  "));
    }

    private ResultActions cancelarComCorpo(Vaga vaga, Usuario usuario, String corpo) throws Exception {
        return mockMvc.perform(delete("/api/vagas/{id}", vaga.getId())
                .header("Authorization", bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private String corpoCancelamento(String motivo) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "confirmacao", true,
                "motivo", motivo));
    }

    private static Stream<String> requisicoesInvalidas() {
        return Stream.of(
                "{\"confirmacao\":false,\"motivo\":\"Motivo válido\"}",
                "{\"motivo\":\"Motivo válido\"}",
                "{\"confirmacao\":true}",
                "{\"confirmacao\":true,\"motivo\":null}",
                "{\"confirmacao\":true,\"motivo\":\"\"}",
                "{\"confirmacao\":true,\"motivo\":\"   \"}");
    }

    private ResultActions detalhar(Vaga vaga, Usuario usuario) throws Exception {
        return mockMvc.perform(get("/api/vagas/{id}", vaga.getId())
                .header("Authorization", bearer(usuario)));
    }

    private ResultActions candidatar(Vaga vaga, PerfilArtista artista) throws Exception {
        String corpo = """
                {
                  "vagaId": %d,
                  "artistaId": %d,
                  "mensagemApresentacao": "Tenho interesse nesta oportunidade.",
                  "linkPortfolioCandidatura": "https://portfolio.example/candidatura"
                }
                """.formatted(vaga.getId(), artista.getUsuarioId());
        return mockMvc.perform(post("/api/candidaturas")
                .header("Authorization", bearer(artista.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private JsonNode listarVagas(Usuario usuario) throws Exception {
        var request = get("/api/vagas");
        if (usuario != null) {
            request.header("Authorization", bearer(usuario));
        }
        MvcResult resultado = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private Set<Long> ids(JsonNode content) {
        if (content == null || !content.isArray()) {
            return Set.of();
        }
        return java.util.stream.StreamSupport.stream(content.spliterator(), false)
                .map(item -> item.get("id").asLong())
                .collect(Collectors.toSet());
    }

    private StatusVaga statusPersistido(Vaga vaga) {
        return vagaRepository.findById(vaga.getId()).orElseThrow().getStatus();
    }

    private int quantidadeLogs(Vaga vaga) {
        return jdbcTemplate.queryForObject(
                "select count(*) from log_vagas_canceladas where vaga_id = ?",
                Integer.class, vaga.getId());
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.gerarToken(usuario);
    }

    private PerfilContratante novoContratante(String email) {
        Usuario usuario = novoUsuario(email, TipoUsuario.CONTRATANTE, false);
        PerfilContratante perfil = new PerfilContratante();
        perfil.setUsuario(usuario);
        perfil.setNomeEmpresa("Empresa RF25");
        return perfilContratanteRepository.save(perfil);
    }

    private PerfilArtista novoArtista(String email) {
        Usuario usuario = novoUsuario(email, TipoUsuario.ARTISTA, true);
        PerfilArtista perfil = new PerfilArtista();
        perfil.setUsuario(usuario);
        perfil.setBiografia("Biografia profissional");
        perfil.setLocalizacao("São Paulo, SP");
        perfil.setUrlPortfolio("https://portfolio.example");
        perfil.setUltimaAtualizacao(LocalDateTime.now());
        return perfilArtistaRepository.save(perfil);
    }

    private Usuario novoUsuario(String email, TipoUsuario tipo, boolean perfilCompleto) {
        Usuario usuario = new Usuario();
        usuario.setNome(tipo == TipoUsuario.ARTISTA ? "Artista RF25" : "Contratante RF25");
        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setTelefone("11999999999");
        usuario.setEmail(email);
        usuario.setSenha("hash-teste");
        usuario.setTipoUsuario(tipo);
        usuario.setPerfilCompleto(perfilCompleto);
        usuario.setDataCriacao(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    private Vaga novaVaga(PerfilContratante contratante, StatusVaga status) {
        Vaga vaga = new Vaga();
        vaga.setContratante(contratante);
        vaga.setTitulo("Vaga RF25");
        vaga.setDescricao("Descrição da vaga");
        vaga.setRequisitos("Requisitos profissionais");
        vaga.setRemuneraValor(new BigDecimal("2500.00"));
        vaga.setFormaPagamento("Pix");
        vaga.setCidade("Campinas");
        vaga.setEstado("SP");
        vaga.setModeloTrabalho(ModeloTrabalho.HIBRIDO);
        vaga.setTipoContrato("Freelance");
        vaga.setStatus(status);
        vaga.setDataPublicacao(LocalDateTime.now());
        return vagaRepository.save(vaga);
    }

    private Candidatura novaCandidatura(
            Vaga vaga, PerfilArtista artista, StatusCandidatura status) {
        Candidatura candidatura = new Candidatura();
        candidatura.setVaga(vaga);
        candidatura.setArtista(artista);
        candidatura.setMensagemApresentacao("Mensagem profissional");
        candidatura.setLinkPortfolioCandidatura("https://portfolio.example/candidatura");
        candidatura.setStatus(status);
        candidatura.setDataCandidatura(LocalDateTime.now());
        return candidaturaRepository.save(candidatura);
    }
}
