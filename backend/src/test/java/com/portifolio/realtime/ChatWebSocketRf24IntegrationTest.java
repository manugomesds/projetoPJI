package com.portifolio.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portifolio.model.ParticipanteChat;
import com.portifolio.model.SalaChat;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.TipoNotificacao;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.MensagemChatRepository;
import com.portifolio.repository.NotificacaoRepository;
import com.portifolio.repository.ParticipanteChatRepository;
import com.portifolio.repository.SalaChatRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.security.JwtService;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketRf24IntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScripts("db/schema-test.sql", "db/catalogo-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @LocalServerPort int port;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired SalaChatRepository salaChatRepository;
    @Autowired ParticipanteChatRepository participanteChatRepository;
    @Autowired MensagemChatRepository mensagemChatRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JwtService jwtService;

    @AfterEach
    void limpar() {
        jdbcTemplate.execute("TRUNCATE notificacoes, mensagens_chat, participantes_chat, "
                + "salas_chat, usuarios RESTART IDENTITY CASCADE");
    }

    @Test
    void sendRealPersisteEEntregaSomenteAosDoisParticipantesEmMenosDeCincoSegundos()
            throws Exception {
        Usuario artista = usuario("artista-ws@rf24.test", TipoUsuario.ARTISTA);
        Usuario contratante = usuario("contratante-ws@rf24.test", TipoUsuario.CONTRATANTE);
        Usuario terceiro = usuario("terceiro-ws@rf24.test", TipoUsuario.ARTISTA);
        SalaChat sala = sala(artista, contratante);
        WebSocketStompClient cliente = cliente();
        StompSession sessaoArtista = conectar(cliente, artista);
        StompSession sessaoContratante = conectar(cliente, contratante);
        StompSession sessaoTerceiro = conectar(cliente, terceiro);
        BlockingQueue<JsonNode> artistaRecebeu = new LinkedBlockingQueue<>();
        BlockingQueue<JsonNode> contratanteRecebeu = new LinkedBlockingQueue<>();
        BlockingQueue<JsonNode> terceiroRecebeu = new LinkedBlockingQueue<>();
        sessaoArtista.subscribe("/user/queue/chat", handler(artistaRecebeu));
        sessaoContratante.subscribe("/user/queue/chat", handler(contratanteRecebeu));
        sessaoTerceiro.subscribe("/user/queue/chat", handler(terceiroRecebeu));
        Thread.sleep(150);

        long inicio = System.nanoTime();
        sessaoArtista.send(
                "/app/chat/salas/" + sala.getId() + "/mensagens",
                Map.of("texto", "Mensagem WebSocket RF24"));
        JsonNode recebidaArtista = artistaRecebeu.poll(5, TimeUnit.SECONDS);
        JsonNode recebidaContratante = contratanteRecebeu.poll(5, TimeUnit.SECONDS);
        Duration duracao = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(recebidaArtista).isNotNull();
        assertThat(recebidaContratante).isNotNull();
        assertThat(recebidaArtista.path("tipo").asText()).isEqualTo("NOVA_MENSAGEM");
        assertThat(recebidaContratante.path("mensagem").path("texto").asText())
                .isEqualTo("Mensagem WebSocket RF24");
        assertThat(recebidaContratante.path("mensagem").path("remetenteId").asLong())
                .isEqualTo(artista.getId());
        assertThat(duracao).isLessThan(Duration.ofSeconds(5));
        assertThat(terceiroRecebeu.poll(700, TimeUnit.MILLISECONDS)).isNull();
        assertThat(mensagemChatRepository.findAll()).singleElement().satisfies(mensagem -> {
            assertThat(mensagem.getRemetente().getId()).isEqualTo(artista.getId());
            assertThat(mensagem.getTexto()).isEqualTo("Mensagem WebSocket RF24");
        });
        assertThat(notificacaoRepository.findAll()).singleElement().satisfies(notificacao -> {
            assertThat(notificacao.getUsuarioDestino().getId()).isEqualTo(contratante.getId());
            assertThat(notificacao.getTipo()).isEqualTo(TipoNotificacao.MENSAGEM);
        });

        sessaoArtista.disconnect();
        sessaoContratante.disconnect();
        sessaoTerceiro.disconnect();
        cliente.stop();
    }

    private WebSocketStompClient cliente() {
        WebSocketStompClient cliente = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(new ObjectMapper().findAndRegisterModules());
        cliente.setMessageConverter(converter);
        return cliente;
    }

    private StompSession conectar(WebSocketStompClient cliente, Usuario usuario) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwtService.gerarToken(usuario));
        WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
        httpHeaders.setOrigin("http://localhost:3000");
        return cliente.connectAsync(
                        "ws://localhost:" + port + "/ws",
                        httpHeaders,
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private StompFrameHandler handler(BlockingQueue<JsonNode> fila) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                fila.add((JsonNode) payload);
            }
        };
    }

    private Usuario usuario(String email, TipoUsuario tipo) {
        Usuario usuario = new Usuario();
        usuario.setNome("Pessoa RF24 WebSocket");
        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setTelefone("11999999999");
        usuario.setEmail(email);
        usuario.setSenha("{noop}teste");
        usuario.setTipoUsuario(tipo);
        usuario.setPerfilCompleto(true);
        usuario.setDataCriacao(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    private SalaChat sala(Usuario a, Usuario b) {
        SalaChat sala = new SalaChat();
        sala.setDataCriacao(LocalDateTime.now());
        sala = salaChatRepository.saveAndFlush(sala);
        participanteChatRepository.saveAllAndFlush(List.of(
                new ParticipanteChat(sala, a), new ParticipanteChat(sala, b)));
        return sala;
    }
}
