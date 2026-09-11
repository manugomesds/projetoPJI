package com.portifolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portifolio.model.Candidatura;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Usuario;
import com.portifolio.model.Vaga;
import com.portifolio.model.enums.ModeloTrabalho;
import com.portifolio.model.enums.StatusCandidatura;
import com.portifolio.model.enums.StatusVaga;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.CandidaturaRepository;
import com.portifolio.repository.NotificacaoRepository;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.repository.VagaRepository;
import com.portifolio.security.JwtService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "app.vagas.auto-close.enabled=false")
@AutoConfigureMockMvc
@Import(VagaPrazoRf23Rf06IntegrationTest.FixedClockConfig.class)
class VagaPrazoRf23Rf06IntegrationTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.of(2026, 9, 11);

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
    @Autowired VagaRepository vagaRepository;
    @Autowired CandidaturaRepository candidaturaRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired VagaPrazoService vagaPrazoService;
    @Autowired JwtService jwtService;

    @AfterEach
    void limparBanco() {
        jdbcTemplate.execute("TRUNCATE notificacoes, candidaturas, vagas, perfis_artistas, "
                + "perfis_contratantes, usuarios RESTART IDENTITY CASCADE");
    }

    @Test
    void vagaAbertaSemPrazoAceitaCandidatura() throws Exception {
        PerfilContratante dono = novoContratante("sem-prazo-dono@prazo.test");
        PerfilArtista artista = novoArtista("sem-prazo-artista@prazo.test", true);
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA, null);

        candidatar(vaga, artista)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"));

        assertThat(candidaturaRepository.count()).isOne();
        assertThat(notificacaoRepository.count()).isOne();
    }

    @Test
    void vagaAbertaComPrazoFuturoAceitaCandidatura() throws Exception {
        PerfilContratante dono = novoContratante("futura-dono@prazo.test");
        PerfilArtista artista = novoArtista("futura-artista@prazo.test", true);
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA, HOJE.plusDays(1));

        candidatar(vaga, artista).andExpect(status().isCreated());

        assertThat(candidaturaRepository.count()).isOne();
        assertThat(notificacaoRepository.count()).isOne();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void prazoPassadoOuNoProprioDiaBloqueiaSemPersistirOuNotificar(int deslocamento) throws Exception {
        PerfilContratante dono = novoContratante("vencida-dono-" + deslocamento + "@prazo.test");
        PerfilArtista artista = novoArtista("vencida-artista-" + deslocamento + "@prazo.test", true);
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA, HOJE.plusDays(deslocamento));

        candidatar(vaga, artista)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(
                        "A vaga não aceita mais candidaturas porque a data limite foi atingida."));

        assertThat(candidaturaRepository.count()).isZero();
        assertThat(notificacaoRepository.count()).isZero();
        assertThat(vagaRepository.findById(vaga.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusVaga.ABERTA);
    }

    @Test
    void encerramentoAutomaticoIncluiAbertaEPausadaVencidas() {
        PerfilContratante dono = novoContratante("elegiveis-dono@prazo.test");
        Vaga aberta = novaVaga(dono, StatusVaga.ABERTA, HOJE.minusDays(1));
        Vaga pausada = novaVaga(dono, StatusVaga.PAUSADA, HOJE);

        assertThat(vagaPrazoService.encerrarVencidas()).isEqualTo(2);

        assertThat(statusDaVaga(aberta)).isEqualTo(StatusVaga.ENCERRADA);
        assertThat(statusDaVaga(pausada)).isEqualTo(StatusVaga.ENCERRADA);
    }

    @Test
    void encerramentoAutomaticoPreservaFuturaSemPrazoEEstadosFinais() {
        PerfilContratante dono = novoContratante("preservadas-dono@prazo.test");
        Vaga futura = novaVaga(dono, StatusVaga.ABERTA, HOJE.plusDays(1));
        Vaga semPrazo = novaVaga(dono, StatusVaga.ABERTA, null);
        Vaga pausadaFutura = novaVaga(dono, StatusVaga.PAUSADA, HOJE.plusDays(1));
        Vaga pausadaSemPrazo = novaVaga(dono, StatusVaga.PAUSADA, null);
        Vaga encerrada = novaVaga(dono, StatusVaga.ENCERRADA, HOJE.minusDays(1));
        Vaga cancelada = novaVaga(dono, StatusVaga.CANCELADA, HOJE.minusDays(1));

        assertThat(vagaPrazoService.encerrarVencidas()).isZero();

        assertThat(statusDaVaga(futura)).isEqualTo(StatusVaga.ABERTA);
        assertThat(statusDaVaga(semPrazo)).isEqualTo(StatusVaga.ABERTA);
        assertThat(statusDaVaga(pausadaFutura)).isEqualTo(StatusVaga.PAUSADA);
        assertThat(statusDaVaga(pausadaSemPrazo)).isEqualTo(StatusVaga.PAUSADA);
        assertThat(statusDaVaga(encerrada)).isEqualTo(StatusVaga.ENCERRADA);
        assertThat(statusDaVaga(cancelada)).isEqualTo(StatusVaga.CANCELADA);
    }

    @Test
    void repeticaoEIdempotenteENotificaUmaVezSomenteOsCandidatos() {
        PerfilContratante dono = novoContratante("idempotente-dono@prazo.test");
        PerfilArtista primeiro = novoArtista("idempotente-1@prazo.test", true);
        PerfilArtista segundo = novoArtista("idempotente-2@prazo.test", true);
        PerfilArtista terceiro = novoArtista("idempotente-fora@prazo.test", true);
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA, HOJE.minusDays(1));
        novaCandidatura(vaga, primeiro);
        novaCandidatura(vaga, segundo);

        assertThat(vagaPrazoService.encerrarVencidas()).isOne();
        assertThat(vagaPrazoService.encerrarVencidas()).isZero();

        List<Long> destinatarios = jdbcTemplate.queryForList(
                "select usuario_destino_id from notificacoes order by usuario_destino_id",
                Long.class);
        assertThat(destinatarios).containsExactlyInAnyOrder(
                primeiro.getUsuarioId(), segundo.getUsuarioId());
        assertThat(destinatarios).doesNotContain(dono.getUsuarioId(), terceiro.getUsuarioId());
        assertThat(jdbcTemplate.queryForList(
                "select mensagem_alerta from notificacoes", String.class))
                .allMatch(mensagem -> mensagem.contains("encerrada"));
    }

    @Test
    void duasExecucoesConcorrentesProduzemUmaTransicaoEUmaNotificacao() throws Exception {
        PerfilContratante dono = novoContratante("concorrente-dono@prazo.test");
        PerfilArtista artista = novoArtista("concorrente-artista@prazo.test", true);
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA, HOJE);
        novaCandidatura(vaga, artista);
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> primeira = executor.submit(
                    () -> executarAposBarreira(prontas, iniciar));
            Future<Integer> segunda = executor.submit(
                    () -> executarAposBarreira(prontas, iniciar));
            prontas.await();
            iniciar.countDown();

            assertThat(primeira.get() + segunda.get()).isOne();
        } finally {
            executor.shutdownNow();
        }

        assertThat(statusDaVaga(vaga)).isEqualTo(StatusVaga.ENCERRADA);
        assertThat(notificacaoRepository.count()).isOne();
        assertThat(notificacaoRepository.findAll().getFirst().getUsuarioDestino().getId())
                .isEqualTo(artista.getUsuarioId());
    }

    @Test
    void candidaturaConcorrenteComEncerramentoNaoUltrapassaPrazo() throws Exception {
        PerfilContratante dono = novoContratante("corrida-dono@prazo.test");
        PerfilArtista artista = novoArtista("corrida-artista@prazo.test", true);
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA, HOJE);
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> candidatura = executor.submit(() -> {
                prontas.countDown();
                iniciar.await();
                return candidatar(vaga, artista).andReturn().getResponse().getStatus();
            });
            Future<Integer> encerramento = executor.submit(
                    () -> executarAposBarreira(prontas, iniciar));
            prontas.await();
            iniciar.countDown();

            assertThat(candidatura.get()).isEqualTo(422);
            assertThat(encerramento.get()).isOne();
        } finally {
            executor.shutdownNow();
        }

        assertThat(candidaturaRepository.count()).isZero();
        assertThat(notificacaoRepository.count()).isZero();
        assertThat(statusDaVaga(vaga)).isEqualTo(StatusVaga.ENCERRADA);
    }

    private int executarAposBarreira(CountDownLatch prontas, CountDownLatch iniciar)
            throws InterruptedException {
        prontas.countDown();
        iniciar.await();
        return vagaPrazoService.encerrarVencidas();
    }

    private org.springframework.test.web.servlet.ResultActions candidatar(
            Vaga vaga, PerfilArtista artista) throws Exception {
        return mockMvc.perform(post("/api/candidaturas")
                .header("Authorization", "Bearer " + jwtService.gerarToken(artista.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vagaId":%d,"mensagemApresentacao":"Tenho interesse.",
                        "linkPortfolioCandidatura":"https://portfolio.example/prazo"}
                        """.formatted(vaga.getId())));
    }

    private StatusVaga statusDaVaga(Vaga vaga) {
        return vagaRepository.findById(vaga.getId()).orElseThrow().getStatus();
    }

    private Usuario novoUsuario(String email, TipoUsuario tipo, boolean perfilCompleto) {
        Usuario usuario = new Usuario();
        usuario.setNome("Pessoa Prazo");
        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setTelefone("11999999999");
        usuario.setEmail(email);
        usuario.setSenha("{noop}senha-teste");
        usuario.setTipoUsuario(tipo);
        usuario.setPerfilCompleto(perfilCompleto);
        usuario.setDataCriacao(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    private PerfilContratante novoContratante(String email) {
        PerfilContratante perfil = new PerfilContratante();
        perfil.setUsuario(novoUsuario(email, TipoUsuario.CONTRATANTE, false));
        perfil.setNomeEmpresa("Empresa Prazo");
        return perfilContratanteRepository.save(perfil);
    }

    private PerfilArtista novoArtista(String email, boolean perfilCompleto) {
        PerfilArtista perfil = new PerfilArtista();
        perfil.setUsuario(novoUsuario(email, TipoUsuario.ARTISTA, perfilCompleto));
        perfil.setBiografia("Biografia Prazo");
        return perfilArtistaRepository.save(perfil);
    }

    private Vaga novaVaga(
            PerfilContratante contratante, StatusVaga status, LocalDate dataLimite) {
        Vaga vaga = new Vaga();
        vaga.setContratante(contratante);
        vaga.setTitulo("Vaga Prazo " + status + " " + dataLimite);
        vaga.setDescricao("Descrição da vaga");
        vaga.setRequisitos("Requisitos da vaga");
        vaga.setRemuneraValor(new BigDecimal("1000.00"));
        vaga.setFormaPagamento("Pix");
        vaga.setCidade("São Paulo");
        vaga.setEstado("SP");
        vaga.setModeloTrabalho(ModeloTrabalho.REMOTO);
        vaga.setTipoContrato("Freelance");
        vaga.setStatus(status);
        vaga.setDataLimiteCandidatura(dataLimite);
        vaga.setDataPublicacao(LocalDateTime.now());
        vaga.setTags(new HashSet<>());
        return vagaRepository.save(vaga);
    }

    private Candidatura novaCandidatura(Vaga vaga, PerfilArtista artista) {
        Candidatura candidatura = new Candidatura();
        candidatura.setVaga(vaga);
        candidatura.setArtista(artista);
        candidatura.setMensagemApresentacao("Tenho interesse.");
        candidatura.setLinkPortfolioCandidatura("https://portfolio.example/prazo");
        candidatura.setStatus(StatusCandidatura.PENDENTE);
        candidatura.setDataCandidatura(LocalDateTime.now());
        return candidaturaRepository.save(candidatura);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-11T15:00:00Z"), FUSO);
        }
    }
}
