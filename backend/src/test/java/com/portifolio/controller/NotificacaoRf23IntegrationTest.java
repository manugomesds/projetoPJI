package com.portifolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portifolio.model.Candidatura;
import com.portifolio.model.Notificacao;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Usuario;
import com.portifolio.model.Vaga;
import com.portifolio.model.enums.ModeloTrabalho;
import com.portifolio.model.enums.StatusCandidatura;
import com.portifolio.model.enums.StatusVaga;
import com.portifolio.model.enums.TipoNotificacao;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.CandidaturaRepository;
import com.portifolio.repository.NotificacaoRepository;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.repository.VagaRepository;
import com.portifolio.security.JwtService;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class NotificacaoRf23IntegrationTest {

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
    @Autowired VagaRepository vagaRepository;
    @Autowired CandidaturaRepository candidaturaRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired JwtService jwtService;
    @Autowired EntityManagerFactory entityManagerFactory;

    @AfterEach
    void limparBanco() {
        jdbcTemplate.execute("ALTER TABLE candidaturas DROP CONSTRAINT IF EXISTS rf23_forcar_rollback");
        jdbcTemplate.execute("ALTER TABLE notificacoes DROP CONSTRAINT IF EXISTS rf23_forcar_falha");
        jdbcTemplate.execute("TRUNCATE notificacoes, candidaturas, vagas, perfis_artistas, "
                + "perfis_contratantes, usuarios RESTART IDENTITY CASCADE");
    }

    @Test
    void endpointsExigemJwtSemAceitarTokenNaUrl() throws Exception {
        mockMvc.perform(get("/api/notificacoes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notificacoes/nao-lidas/count")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notificacoes/stream?token=qualquer"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notificacoes/lidas")).andExpect(status().isUnauthorized());
    }

    @Test
    void listaSomenteDoUsuarioComPaginacaoOrdenacaoEstavelEWhitelist() throws Exception {
        Usuario dono = novoUsuario("dono-lista@rf23.test", TipoUsuario.ARTISTA);
        Usuario outro = novoUsuario("outro-lista@rf23.test", TipoUsuario.ARTISTA);
        LocalDateTime data = LocalDateTime.now();
        for (int i = 0; i < 3; i++) {
            novaNotificacao(dono, "Alerta " + i, data.plusSeconds(i), false);
        }
        novaNotificacao(outro, "Privada", data.plusMinutes(1), false);

        mockMvc.perform(get("/api/notificacoes?page=0&size=2")
                        .header("Authorization", bearer(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].mensagem").value("Alerta 2"))
                .andExpect(jsonPath("$.content[1].mensagem").value("Alerta 1"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content[0].usuarioDestino").doesNotExist())
                .andExpect(jsonPath("$.content[0].usuario").doesNotExist());
    }

    @Test
    void aplicaPadraoVinteELimiteMaximoCinquenta() throws Exception {
        Usuario usuario = novoUsuario("limites@rf23.test", TipoUsuario.ARTISTA);
        for (int i = 0; i < 55; i++) {
            novaNotificacao(usuario, "N" + i, LocalDateTime.now().plusNanos(i), false);
        }

        mockMvc.perform(get("/api/notificacoes")
                        .header("Authorization", bearer(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.size").value(20));
        mockMvc.perform(get("/api/notificacoes?size=999")
                        .header("Authorization", bearer(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(50))
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void contaNaoLidasMarcaUmaETodasSemIdor() throws Exception {
        Usuario dono = novoUsuario("leitura@rf23.test", TipoUsuario.CONTRATANTE);
        Usuario outro = novoUsuario("idor@rf23.test", TipoUsuario.CONTRATANTE);
        Notificacao primeira = novaNotificacao(dono, "Primeira", LocalDateTime.now(), false);
        novaNotificacao(dono, "Segunda", LocalDateTime.now().plusSeconds(1), false);

        mockMvc.perform(get("/api/notificacoes/nao-lidas/count")
                        .header("Authorization", bearer(dono)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(patch("/api/notificacoes/{id}/lida", primeira.getId())
                        .header("Authorization", bearer(outro)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/notificacoes/{id}/lida", primeira.getId())
                        .header("Authorization", bearer(dono)))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/notificacoes/lidas")
                        .header("Authorization", bearer(dono)))
                .andExpect(status().isNoContent());
        assertThat(notificacaoRepository.countByUsuarioDestinoIdAndLidaFalse(dono.getId())).isZero();
    }

    @Test
    void candidaturaValidaNotificaDonoUmaVezDepoisDoCommit() throws Exception {
        PerfilContratante dono = novoContratante("dono-candidatura@rf23.test");
        PerfilArtista artista = novoArtista("artista-candidatura@rf23.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);

        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCandidatura(vaga, artista)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCandidatura(vaga, artista)))
                .andExpect(status().isConflict());

        List<Notificacao> notificacoes = notificacaoRepository.findAll();
        assertThat(notificacoes).hasSize(1);
        assertThat(notificacoes.getFirst().getUsuarioDestino().getId())
                .isEqualTo(dono.getUsuarioId());
        assertThat(notificacoes.getFirst().getTipo()).isEqualTo(TipoNotificacao.CANDIDATURA);
        assertThat(notificacoes.getFirst().getLink()).isEqualTo("dashboard-contratante.html");
    }

    @Test
    void candidaturaRejeitadaNaoGeraNotificacao() throws Exception {
        PerfilContratante dono = novoContratante("dono-rejeitada@rf23.test");
        PerfilArtista artista = novoArtista("artista-rejeitada@rf23.test");
        Vaga vaga = novaVaga(dono, StatusVaga.PAUSADA);

        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCandidatura(vaga, artista)))
                .andExpect(status().isUnprocessableEntity());
        assertThat(notificacaoRepository.count()).isZero();
    }

    @Test
    void rollbackDaCandidaturaNaoGeraNotificacao() throws Exception {
        PerfilContratante dono = novoContratante("dono-rollback@rf23.test");
        PerfilArtista artista = novoArtista("artista-rollback@rf23.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        jdbcTemplate.execute("ALTER TABLE candidaturas ADD CONSTRAINT rf23_forcar_rollback CHECK (false)");

        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCandidatura(vaga, artista)))
                .andExpect(status().isConflict());
        assertThat(candidaturaRepository.count()).isZero();
        assertThat(notificacaoRepository.count()).isZero();
    }

    @Test
    void falhaAoPersistirNotificacaoNaoDesfazCandidaturaJaConfirmada() throws Exception {
        PerfilContratante dono = novoContratante("dono-falha-notificacao@rf23.test");
        PerfilArtista artista = novoArtista("artista-falha-notificacao@rf23.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        jdbcTemplate.execute("ALTER TABLE notificacoes ADD CONSTRAINT rf23_forcar_falha CHECK (false)");

        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCandidatura(vaga, artista)))
                .andExpect(status().isCreated());
        assertThat(candidaturaRepository.count()).isOne();
        assertThat(notificacaoRepository.count()).isZero();
    }

    @Test
    void rf31NotificaCandidatosEmSuspensaoReaberturaEEncerramento() throws Exception {
        PerfilContratante dono = novoContratante("dono-status@rf23.test");
        PerfilArtista artista = novoArtista("artista-status@rf23.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        novaCandidatura(vaga, artista);

        alterarStatus(vaga, dono.getUsuario(), "SUSPENDER").andExpect(status().isOk());
        alterarStatus(vaga, dono.getUsuario(), "REABRIR").andExpect(status().isOk());
        alterarStatus(vaga, dono.getUsuario(), "ENCERRAR").andExpect(status().isOk());

        List<String> mensagens = notificacaoRepository.findAll().stream()
                .map(Notificacao::getMensagem).toList();
        assertThat(mensagens).hasSize(3);
        assertThat(mensagens).anyMatch(m -> m.contains("suspensa"));
        assertThat(mensagens).anyMatch(m -> m.contains("reaberta"));
        assertThat(mensagens).anyMatch(m -> m.contains("encerrada"));
    }

    @Test
    void rf25NotificaCandidatosEPreservaCanceladaPorVaga() throws Exception {
        PerfilContratante dono = novoContratante("dono-cancelamento@rf23.test");
        PerfilArtista artista = novoArtista("artista-cancelamento@rf23.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        Candidatura candidatura = novaCandidatura(vaga, artista);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/vagas/{id}", vaga.getId())
                        .header("Authorization", bearer(dono.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmacao\":true,\"motivo\":\"Projeto encerrado.\"}"))
                .andExpect(status().isNoContent());

        assertThat(candidaturaRepository.findById(candidatura.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusCandidatura.CANCELADA_POR_VAGA);
        assertThat(notificacaoRepository.findAll()).singleElement()
                .satisfies(n -> assertThat(n.getMensagem()).contains("cancelada"));
    }

    @Test
    void listagemDeCinquentaNaoApresentaNMaisUm() throws Exception {
        Usuario usuario = novoUsuario("nmaisum@rf23.test", TipoUsuario.ARTISTA);
        for (int i = 0; i < 50; i++) {
            novaNotificacao(usuario, "Carga " + i, LocalDateTime.now().plusNanos(i), false);
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        mockMvc.perform(get("/api/notificacoes?size=50")
                        .header("Authorization", bearer(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(50));

        // O custo permanece constante: autenticacao, usuario, count e pagina.
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    private org.springframework.test.web.servlet.ResultActions alterarStatus(
            Vaga vaga, Usuario usuario, String acao) throws Exception {
        return mockMvc.perform(patch("/api/vagas/{id}/status", vaga.getId())
                .header("Authorization", bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acao\":\"" + acao + "\"}"));
    }

    private Usuario novoUsuario(String email, TipoUsuario tipo) {
        Usuario usuario = new Usuario();
        usuario.setNome("Pessoa RF23");
        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setTelefone("11999999999");
        usuario.setEmail(email);
        usuario.setSenha("{noop}teste");
        usuario.setTipoUsuario(tipo);
        usuario.setPerfilCompleto(true);
        usuario.setDataCriacao(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    private PerfilContratante novoContratante(String email) {
        PerfilContratante perfil = new PerfilContratante();
        perfil.setUsuario(novoUsuario(email, TipoUsuario.CONTRATANTE));
        perfil.setNomeEmpresa("Empresa RF23");
        return perfilContratanteRepository.save(perfil);
    }

    private PerfilArtista novoArtista(String email) {
        PerfilArtista perfil = new PerfilArtista();
        perfil.setTipoPerfilArtistico(com.portifolio.model.enums.TipoPerfilArtistico.ARTISTA_SOLO);
        perfil.setRaioAtuacao(com.portifolio.model.enums.Abrangencia.LOCAL);
        perfil.setUsuario(novoUsuario(email, TipoUsuario.ARTISTA));
        perfil.setBiografia("Biografia RF23");
        return perfilArtistaRepository.save(perfil);
    }

    private Vaga novaVaga(PerfilContratante contratante, StatusVaga status) {
        Vaga vaga = new Vaga();
        vaga.setArea(com.portifolio.support.OfficialSchemaFixtures.area());
        vaga.setAbrangencia(com.portifolio.model.enums.Abrangencia.LOCAL);
        vaga.setContratante(contratante);
        vaga.setTitulo("Vaga RF23");
        vaga.setDescricao("Descricao");
        vaga.setRequisitos("Requisitos");
        vaga.setValorMinimo(new BigDecimal("1000.00"));
        vaga.setValorMaximo(new BigDecimal("1000.00"));
        vaga.setFormaRemuneracao(com.portifolio.model.enums.FormaRemuneracao.POR_EVENTO);
        vaga.setCidade("Sao Paulo");
        vaga.setEstado("SP");
        vaga.setModeloTrabalho(ModeloTrabalho.REMOTO);
        vaga.setTipoContrato("Freelance");
        vaga.setStatus(status);
        vaga.setDataPublicacao(LocalDateTime.now());
        vaga.setFuncoes(new HashSet<>());
        return vagaRepository.save(vaga);
    }

    private Candidatura novaCandidatura(Vaga vaga, PerfilArtista artista) {
        Candidatura candidatura = new Candidatura();
        candidatura.setVaga(vaga);
        candidatura.setArtista(artista);
        candidatura.setMensagemApresentacao("Tenho interesse.");
        candidatura.setLinkPortfolioCandidatura("https://example.test/portfolio");
        candidatura.setStatus(StatusCandidatura.PENDENTE);
        candidatura.setDataCandidatura(LocalDateTime.now());
        return candidaturaRepository.save(candidatura);
    }

    private Notificacao novaNotificacao(
            Usuario destino, String mensagem, LocalDateTime data, boolean lida) {
        Notificacao notificacao = new Notificacao();
        notificacao.setUsuarioDestino(destino);
        notificacao.setTipo(TipoNotificacao.CANDIDATURA);
        notificacao.setMensagem(mensagem);
        notificacao.setLink("dashboard-contratante.html");
        notificacao.setLida(lida);
        notificacao.setDataCriacao(data);
        return notificacaoRepository.save(notificacao);
    }

    private String corpoCandidatura(Vaga vaga, PerfilArtista artista) {
        return """
                {"vagaId":%d,"artistaId":%d,"mensagemApresentacao":"Tenho interesse.",
                "linkPortfolioCandidatura":"https://example.test/portfolio"}
                """.formatted(vaga.getId(), artista.getUsuarioId());
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.gerarToken(usuario);
    }
}
