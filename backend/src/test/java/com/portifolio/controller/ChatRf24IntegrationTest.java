package com.portifolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portifolio.model.Candidatura;
import com.portifolio.model.MensagemChat;
import com.portifolio.model.Notificacao;
import com.portifolio.model.ParticipanteChat;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.SalaChat;
import com.portifolio.model.Usuario;
import com.portifolio.model.Vaga;
import com.portifolio.model.enums.ModeloTrabalho;
import com.portifolio.model.enums.StatusCandidatura;
import com.portifolio.model.enums.StatusVaga;
import com.portifolio.model.enums.TipoNotificacao;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.CandidaturaRepository;
import com.portifolio.repository.MensagemChatRepository;
import com.portifolio.repository.NotificacaoRepository;
import com.portifolio.repository.ParticipanteChatRepository;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.SalaChatRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.repository.VagaRepository;
import com.portifolio.security.JwtService;
import com.portifolio.service.ChatService;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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
class ChatRf24IntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScripts("db/schema-test.sql", "db/catalogo-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @Autowired MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PerfilArtistaRepository perfilArtistaRepository;
    @Autowired PerfilContratanteRepository perfilContratanteRepository;
    @Autowired VagaRepository vagaRepository;
    @Autowired CandidaturaRepository candidaturaRepository;
    @Autowired SalaChatRepository salaChatRepository;
    @Autowired ParticipanteChatRepository participanteChatRepository;
    @Autowired MensagemChatRepository mensagemChatRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired JwtService jwtService;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired ChatService chatService;

    @AfterEach
    void limpar() {
        jdbcTemplate.execute("ALTER TABLE mensagens_chat DROP CONSTRAINT IF EXISTS rf24_forcar_rollback");
        jdbcTemplate.execute("TRUNCATE notificacoes, mensagens_chat, participantes_chat, salas_chat, "
                + "candidaturas, vagas, perfis_artistas, perfis_contratantes, usuarios "
                + "RESTART IDENTITY CASCADE");
    }

    @Test
    void endpointsExigemJwt() throws Exception {
        mockMvc.perform(get("/api/chat/salas")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/chat/nao-lidas/count")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/chat/salas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioDestinoId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void criaEReutilizaMesmaSalaNosDoisSentidos() throws Exception {
        Usuario artista = usuario("artista-sala@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        Usuario contratante = usuario("contratante-sala@rf24.test", TipoUsuario.CONTRATANTE, LocalDate.of(1985, 1, 1));

        long primeira = criarSalaViaApi(artista, contratante.getId(), status().isCreated());
        long repetida = criarSalaViaApi(artista, contratante.getId(), status().isCreated());
        long invertida = criarSalaViaApi(contratante, artista.getId(), status().isCreated());

        assertThat(repetida).isEqualTo(primeira);
        assertThat(invertida).isEqualTo(primeira);
        assertThat(salaChatRepository.count()).isOne();
        assertThat(participanteChatRepository.count()).isEqualTo(2);
    }

    @Test
    void criacaoConcorrenteDaMesmaDuplaNaoDuplicaSala() throws Exception {
        Usuario artista = usuario("concorrente@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        Usuario contratante = usuario("concorrente-destino@rf24.test", TipoUsuario.CONTRATANTE, LocalDate.of(1985, 1, 1));
        CountDownLatch largada = new CountDownLatch(1);
        CompletableFuture<Long> primeira = CompletableFuture.supplyAsync(() -> {
            aguardar(largada);
            return chatService.criarOuReutilizarSala(artista.getEmail(), contratante.getId()).getSalaId();
        });
        CompletableFuture<Long> segunda = CompletableFuture.supplyAsync(() -> {
            aguardar(largada);
            return chatService.criarOuReutilizarSala(contratante.getEmail(), artista.getId()).getSalaId();
        });
        largada.countDown();

        assertThat(primeira.get(10, TimeUnit.SECONDS)).isEqualTo(segunda.get(10, TimeUnit.SECONDS));
        assertThat(salaChatRepository.count()).isOne();
        assertThat(participanteChatRepository.count()).isEqualTo(2);
    }

    @Test
    void bloqueiaConversaConsigoMesmoEPapeisIguais() throws Exception {
        Usuario artistaA = usuario("artista-a@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        Usuario artistaB = usuario("artista-b@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1991, 1, 1));

        criarSalaViaApi(artistaA, artistaA.getId(), status().isUnprocessableEntity());
        criarSalaViaApi(artistaA, artistaB.getId(), status().isUnprocessableEntity());
        assertThat(salaChatRepository.count()).isZero();
    }

    @Test
    void menorExigeCandidaturaPersistidaEntreADupla() throws Exception {
        PerfilArtista menor = artista("menor@rf24.test", LocalDate.now().minusYears(16));
        PerfilContratante contratante = contratante("adulto@rf24.test", LocalDate.of(1980, 1, 1));

        criarSalaViaApi(contratante.getUsuario(), menor.getUsuarioId(), status().isUnprocessableEntity());
        Vaga vaga = vaga(contratante);
        candidatura(vaga, menor);

        criarSalaViaApi(contratante.getUsuario(), menor.getUsuarioId(), status().isCreated());
        assertThat(salaChatRepository.count()).isOne();
    }

    @Test
    void listaSomenteSalasDoJwtComPaginacaoEPreview() throws Exception {
        Usuario artista = usuario("lista@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        Usuario terceiro = usuario("terceiro@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        IntStream.range(0, 22).forEach(i -> {
            Usuario destino = usuario("empresa" + i + "@rf24.test", TipoUsuario.CONTRATANTE, LocalDate.of(1980, 1, 1));
            SalaChat sala = sala(artista, destino);
            mensagem(sala, destino, "Mensagem " + i, false, LocalDateTime.now().plusSeconds(i));
        });
        sala(terceiro, usuario("fora@rf24.test", TipoUsuario.CONTRATANTE, LocalDate.of(1980, 1, 1)));

        mockMvc.perform(get("/api/chat/salas").header("Authorization", bearer(artista)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(22))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.content[0].ultimaMensagem").value("Mensagem 21"))
                .andExpect(jsonPath("$.content[0].naoLidas").value(1))
                .andExpect(jsonPath("$.content[0].participanteNome").exists())
                .andExpect(jsonPath("$.content[0].email").doesNotExist());
        mockMvc.perform(get("/api/chat/salas?size=51").header("Authorization", bearer(artista)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/chat/salas?page=1&size=20").header("Authorization", bearer(artista)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void envioUsaJwtPersisteUmaMensagemENotificaSomenteDestinatario() throws Exception {
        Usuario artista = usuario("envio@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        Usuario contratante = usuario("destino@rf24.test", TipoUsuario.CONTRATANTE, LocalDate.of(1980, 1, 1));
        Usuario falsificado = usuario("falso@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        SalaChat sala = sala(artista, contratante);

        mockMvc.perform(post("/api/chat/salas/{id}/mensagens", sala.getId())
                        .header("Authorization", bearer(artista))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"  Olá profissional  \",\"remetenteId\":"
                                + falsificado.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remetenteId").value(artista.getId()))
                .andExpect(jsonPath("$.texto").value("Olá profissional"))
                .andExpect(jsonPath("$.lida").value(false));

        assertThat(mensagemChatRepository.findAll()).singleElement()
                .satisfies(m -> assertThat(m.getRemetente().getId()).isEqualTo(artista.getId()));
        assertThat(notificacaoRepository.findAll()).singleElement().satisfies(n -> {
            assertThat(n.getUsuarioDestino().getId()).isEqualTo(contratante.getId());
            assertThat(n.getTipo()).isEqualTo(TipoNotificacao.MENSAGEM);
            assertThat(n.getMensagem()).doesNotContain("Olá profissional");
            assertThat(n.getLink()).isEqualTo("/mensagens?sala=" + sala.getId());
        });
    }

    @Test
    void envioInvalidoNaoPersisteNemNotifica() throws Exception {
        Usuario artista = usuario("invalido@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        Usuario contratante = usuario("destino-invalido@rf24.test", TipoUsuario.CONTRATANTE, LocalDate.of(1980, 1, 1));
        SalaChat sala = sala(artista, contratante);

        mockMvc.perform(post("/api/chat/salas/{id}/mensagens", sala.getId())
                        .header("Authorization", bearer(artista))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"   \"}"))
                .andExpect(status().isBadRequest());
        assertThat(mensagemChatRepository.count()).isZero();
        assertThat(notificacaoRepository.count()).isZero();
    }

    @Test
    void payloadXssEhPersistidoERetornadoComoTextoLiteral() throws Exception {
        Usuario artista = usuario("xss@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        Usuario contratante = usuario("xss-destino@rf24.test", TipoUsuario.CONTRATANTE, LocalDate.of(1980, 1, 1));
        SalaChat sala = sala(artista, contratante);
        String payload = "<img src=x onerror=alert(1)>";

        mockMvc.perform(post("/api/chat/salas/{id}/mensagens", sala.getId())
                        .header("Authorization", bearer(artista))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("texto", payload))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.texto").value(payload));
        mockMvc.perform(get("/api/chat/salas/{id}/mensagens", sala.getId())
                        .header("Authorization", bearer(contratante)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].texto").value(payload));

        assertThat(mensagemChatRepository.findAll()).singleElement()
                .extracting(MensagemChat::getTexto).isEqualTo(payload);
    }

    @Test
    void historicoEhPaginadoPersistenteOfflineEProtegidoContraIdor() throws Exception {
        Usuario artista = usuario("historico@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        Usuario contratante = usuario("historico-destino@rf24.test", TipoUsuario.CONTRATANTE, LocalDate.of(1980, 1, 1));
        Usuario terceiro = usuario("historico-terceiro@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        SalaChat sala = sala(artista, contratante);
        IntStream.range(0, 23).forEach(i -> mensagem(
                sala, artista, "Offline " + i, false, LocalDateTime.now().plusSeconds(i)));

        mockMvc.perform(get("/api/chat/salas/{id}/mensagens", sala.getId())
                        .header("Authorization", bearer(contratante)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.content[0].texto").value("Offline 22"))
                .andExpect(jsonPath("$.totalElements").value(23))
                .andExpect(jsonPath("$.hasMore").value(true));
        mockMvc.perform(get("/api/chat/salas/{id}/mensagens", sala.getId())
                        .header("Authorization", bearer(terceiro)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/chat/salas/{id}/mensagens?page=1&size=20", sala.getId())
                        .header("Authorization", bearer(contratante)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].texto").value("Offline 2"));
        mockMvc.perform(post("/api/chat/salas/{id}/mensagens", sala.getId())
                        .header("Authorization", bearer(terceiro))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"IDOR\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/chat/salas/{id}/lidas", sala.getId())
                        .header("Authorization", bearer(terceiro)))
                .andExpect(status().isNotFound());
    }

    @Test
    void leituraMarcaSomenteRecebidasENotificaContagemReal() throws Exception {
        Usuario artista = usuario("leitura@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        Usuario contratante = usuario("leitura-destino@rf24.test", TipoUsuario.CONTRATANTE, LocalDate.of(1980, 1, 1));
        SalaChat sala = sala(artista, contratante);
        MensagemChat recebida = mensagem(sala, artista, "Recebida", false, LocalDateTime.now());
        MensagemChat propria = mensagem(sala, contratante, "Própria", false, LocalDateTime.now().plusSeconds(1));

        mockMvc.perform(get("/api/chat/nao-lidas/count").header("Authorization", bearer(contratante)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/dashboard").header("Authorization", bearer(contratante)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagens.disponivel").value(true))
                .andExpect(jsonPath("$.mensagens.quantidadeNaoLidas").value(1));
        mockMvc.perform(patch("/api/chat/salas/{id}/lidas", sala.getId())
                        .header("Authorization", bearer(artista)))
                .andExpect(status().isNoContent());
        assertThat(mensagemChatRepository.findById(recebida.getId()).orElseThrow().getLida()).isFalse();
        assertThat(mensagemChatRepository.findById(propria.getId()).orElseThrow().getLida()).isTrue();
        mockMvc.perform(patch("/api/chat/salas/{id}/lidas", sala.getId())
                        .header("Authorization", bearer(contratante)))
                .andExpect(status().isNoContent());
        assertThat(mensagemChatRepository.findById(recebida.getId()).orElseThrow().getLida()).isTrue();
        assertThat(mensagemChatRepository.findById(propria.getId()).orElseThrow().getLida()).isTrue();
    }

    @Test
    void falhaDePersistenciaFazRollbackSemMensagemENotificacao() throws Exception {
        Usuario artista = usuario("rollback@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        Usuario contratante = usuario("rollback-destino@rf24.test", TipoUsuario.CONTRATANTE, LocalDate.of(1980, 1, 1));
        SalaChat sala = sala(artista, contratante);
        jdbcTemplate.execute("ALTER TABLE mensagens_chat ADD CONSTRAINT rf24_forcar_rollback CHECK (false)");

        mockMvc.perform(post("/api/chat/salas/{id}/mensagens", sala.getId())
                        .header("Authorization", bearer(artista))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Deve falhar\"}"))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mensagens_chat", Long.class)).isZero();
        assertThat(notificacaoRepository.count()).isZero();
    }

    @Test
    void edicaoRespeitaAutorJanelaDeQuinzeMinutosEExclusao() throws Exception {
        Usuario artista = usuario("edicao@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        Usuario contratante = usuario("edicao-destino@rf24.test", TipoUsuario.CONTRATANTE, LocalDate.of(1980, 1, 1));
        SalaChat sala = sala(artista, contratante);
        MensagemChat dentro = mensagem(sala, artista, "Original", false, LocalDateTime.now().minusMinutes(14).minusSeconds(59));
        MensagemChat fora = mensagem(sala, artista, "Antiga", false, LocalDateTime.now().minusMinutes(16));

        mockMvc.perform(patch("/api/chat/mensagens/{id}", dentro.getId())
                        .header("Authorization", bearer(artista))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Corrigida\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.texto").value("Corrigida"));
        mockMvc.perform(patch("/api/chat/mensagens/{id}", dentro.getId())
                        .header("Authorization", bearer(contratante))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Fraude\"}"))
                .andExpect(status().isNotFound());
        Usuario terceiro = usuario("edicao-terceiro@rf24.test", TipoUsuario.CONTRATANTE, LocalDate.of(1980, 1, 1));
        mockMvc.perform(patch("/api/chat/mensagens/{id}", dentro.getId())
                        .header("Authorization", bearer(terceiro))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"IDOR\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/chat/mensagens/{id}", fora.getId())
                        .header("Authorization", bearer(artista))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Tarde\"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(delete("/api/chat/mensagens/{id}", dentro.getId())
                        .header("Authorization", bearer(artista)))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/chat/mensagens/{id}", dentro.getId())
                        .header("Authorization", bearer(artista))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Reviver\"}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(mensagemChatRepository.findById(dentro.getId()).orElseThrow().getTexto())
                .isEqualTo(ChatService.MENSAGEM_EXCLUIDA);
    }

    @Test
    void somenteAutorExcluiEMensagemPermaneceNoHistoricoComoPlaceholder() throws Exception {
        Usuario artista = usuario("exclusao@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        Usuario contratante = usuario("exclusao-destino@rf24.test", TipoUsuario.CONTRATANTE, LocalDate.of(1980, 1, 1));
        Usuario terceiro = usuario("exclusao-terceiro@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        SalaChat sala = sala(artista, contratante);
        MensagemChat mensagem = mensagem(sala, artista, "Excluir", false, LocalDateTime.now());

        mockMvc.perform(delete("/api/chat/mensagens/{id}", mensagem.getId())
                        .header("Authorization", bearer(contratante)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/chat/mensagens/{id}", mensagem.getId())
                        .header("Authorization", bearer(terceiro)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/chat/mensagens/{id}", mensagem.getId())
                        .header("Authorization", bearer(artista)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/chat/salas/{id}/mensagens", sala.getId())
                        .header("Authorization", bearer(contratante)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].texto").value(ChatService.MENSAGEM_EXCLUIDA))
                .andExpect(jsonPath("$.content[0].excluida").value(true));
        assertThat(mensagemChatRepository.count()).isOne();
    }

    @Test
    void listagemDeVinteSalasMantemTetoConstanteDeConsultas() throws Exception {
        Usuario artista = usuario("nmaisum-chat@rf24.test", TipoUsuario.ARTISTA, LocalDate.of(1990, 1, 1));
        IntStream.range(0, 20).forEach(i -> {
            Usuario destino = usuario("nmaisum-empresa" + i + "@rf24.test", TipoUsuario.CONTRATANTE, LocalDate.of(1980, 1, 1));
            SalaChat sala = sala(artista, destino);
            mensagem(sala, destino, "N+1 " + i, false, LocalDateTime.now().plusSeconds(i));
        });
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        statistics.setStatisticsEnabled(true);

        mockMvc.perform(get("/api/chat/salas?size=20").header("Authorization", bearer(artista)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(20));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    private long criarSalaViaApi(
            Usuario autor, Long destinoId,
            org.springframework.test.web.servlet.ResultMatcher statusEsperado) throws Exception {
        String json = mockMvc.perform(post("/api/chat/salas")
                        .header("Authorization", bearer(autor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioDestinoId\":" + destinoId + "}"))
                .andExpect(statusEsperado)
                .andReturn().getResponse().getContentAsString();
        if (json.isBlank()) return -1;
        JsonNode node = objectMapper.readTree(json);
        return node.path("salaId").asLong(-1);
    }

    private Usuario usuario(String email, TipoUsuario tipo, LocalDate nascimento) {
        Usuario usuario = new Usuario();
        usuario.setNome("Pessoa RF24 " + email.substring(0, email.indexOf('@')));
        usuario.setDataNascimento(nascimento);
        usuario.setTelefone("11999999999");
        usuario.setEmail(email);
        usuario.setSenha("{noop}teste");
        usuario.setTipoUsuario(tipo);
        usuario.setPerfilCompleto(true);
        usuario.setDataCriacao(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    private PerfilArtista artista(String email, LocalDate nascimento) {
        PerfilArtista perfil = new PerfilArtista();
        perfil.setTipoPerfilArtistico(com.portifolio.model.enums.TipoPerfilArtistico.ARTISTA_SOLO);
        perfil.setRaioAtuacao(com.portifolio.model.enums.Abrangencia.LOCAL);
        perfil.setUsuario(usuario(email, TipoUsuario.ARTISTA, nascimento));
        perfil.setBiografia("Artista RF24");
        return perfilArtistaRepository.save(perfil);
    }

    private PerfilContratante contratante(String email, LocalDate nascimento) {
        PerfilContratante perfil = new PerfilContratante();
        perfil.setUsuario(usuario(email, TipoUsuario.CONTRATANTE, nascimento));
        perfil.setNomeEmpresa("Empresa RF24");
        return perfilContratanteRepository.save(perfil);
    }

    private Vaga vaga(PerfilContratante contratante) {
        Vaga vaga = new Vaga();
        vaga.setArea(com.portifolio.support.OfficialSchemaFixtures.area());
        vaga.setAbrangencia(com.portifolio.model.enums.Abrangencia.LOCAL);
        vaga.setContratante(contratante);
        vaga.setTitulo("Vaga RF24");
        vaga.setDescricao("Descricao");
        vaga.setRequisitos("Requisitos");
        vaga.setValorMinimo(new BigDecimal("1000.00"));
        vaga.setValorMaximo(new BigDecimal("1000.00"));
        vaga.setFormaRemuneracao(com.portifolio.model.enums.FormaRemuneracao.POR_EVENTO);
        vaga.setCidade("Sao Paulo");
        vaga.setEstado("SP");
        vaga.setModeloTrabalho(ModeloTrabalho.REMOTO);
        vaga.setTipoContrato("Freelance");
        vaga.setStatus(StatusVaga.ABERTA);
        vaga.setDataPublicacao(LocalDateTime.now());
        vaga.setFuncoes(new HashSet<>());
        return vagaRepository.save(vaga);
    }

    private Candidatura candidatura(Vaga vaga, PerfilArtista artista) {
        Candidatura candidatura = new Candidatura();
        candidatura.setVaga(vaga);
        candidatura.setArtista(artista);
        candidatura.setMensagemApresentacao("Interacao profissional");
        candidatura.setLinkPortfolioCandidatura("https://example.test/portfolio");
        candidatura.setStatus(StatusCandidatura.PENDENTE);
        candidatura.setDataCandidatura(LocalDateTime.now());
        return candidaturaRepository.saveAndFlush(candidatura);
    }

    private SalaChat sala(Usuario a, Usuario b) {
        SalaChat sala = new SalaChat();
        sala.setDataCriacao(LocalDateTime.now());
        sala = salaChatRepository.saveAndFlush(sala);
        participanteChatRepository.saveAllAndFlush(List.of(
                new ParticipanteChat(sala, a), new ParticipanteChat(sala, b)));
        return sala;
    }

    private MensagemChat mensagem(
            SalaChat sala, Usuario remetente, String texto, boolean lida, LocalDateTime data) {
        MensagemChat mensagem = new MensagemChat();
        mensagem.setSala(sala);
        mensagem.setRemetente(remetente);
        mensagem.setTexto(texto);
        mensagem.setLida(lida);
        mensagem.setDataEnvio(data);
        return mensagemChatRepository.save(mensagem);
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.gerarToken(usuario);
    }

    private void aguardar(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(erro);
        }
    }
}
