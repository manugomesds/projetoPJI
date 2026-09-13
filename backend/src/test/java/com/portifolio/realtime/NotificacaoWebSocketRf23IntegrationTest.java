package com.portifolio.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.portifolio.dto.NotificacaoResponse;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.TipoNotificacao;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.lang.reflect.Type;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
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
class NotificacaoWebSocketRf23IntegrationTest {

    private static final String SECRET = requireTestSecret();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScripts("db/schema-test.sql", "db/catalogo-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @LocalServerPort int port;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JwtService jwtService;
    @Autowired NotificacaoRealtimeGateway realtimeGateway;

    private static String requireTestSecret() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET deve ser fornecido externamente para o teste WebSocket");
        }
        return secret;
    }

    @AfterEach
    void limpar() {
        jdbcTemplate.execute("TRUNCATE notificacoes, usuarios RESTART IDENTITY CASCADE");
    }

    @Test
    void jwtValidoEntregaNaFilaPrivadaCorretaEmMenosDeCincoSegundos() throws Exception {
        Usuario destino = novoUsuario("destino-ws@rf23.test");
        Usuario isolado = novoUsuario("isolado-ws@rf23.test");
        WebSocketStompClient cliente = cliente();
        StompSession sessaoDestino = conectar(cliente, jwtService.gerarToken(destino));
        StompSession sessaoIsolada = conectar(cliente, jwtService.gerarToken(isolado));
        BlockingQueue<JsonNode> recebidasDestino = new LinkedBlockingQueue<>();
        BlockingQueue<JsonNode> recebidasIsolado = new LinkedBlockingQueue<>();
        sessaoDestino.subscribe("/user/queue/notificacoes", handler(recebidasDestino));
        sessaoIsolada.subscribe("/user/queue/notificacoes", handler(recebidasIsolado));
        Thread.sleep(150);

        NotificacaoResponse alerta = NotificacaoResponse.builder()
                .id(99L).tipo(TipoNotificacao.CANDIDATURA).mensagem("Entrega privada")
                .link("dashboard-contratante.html").lida(false).data(LocalDateTime.now()).build();
        long inicio = System.nanoTime();
        realtimeGateway.entregar(destino.getId(), destino.getEmail(), alerta);
        JsonNode recebida = recebidasDestino.poll(5, TimeUnit.SECONDS);
        Duration duracao = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(recebida).isNotNull();
        assertThat(recebida.path("id").asLong()).isEqualTo(99L);
        assertThat(duracao).isLessThan(Duration.ofSeconds(5));
        assertThat(recebidasIsolado.poll(700, TimeUnit.MILLISECONDS)).isNull();
        sessaoDestino.disconnect();
        sessaoIsolada.disconnect();
        cliente.stop();
    }

    @Test
    void connectSemJwtComJwtInvalidoEComJwtExpiradoSaoRejeitados() {
        Usuario usuario = novoUsuario("seguranca-ws@rf23.test");
        WebSocketStompClient cliente = cliente();

        assertConexaoRejeitada(cliente, null);
        assertConexaoRejeitada(cliente, "jwt-invalido");
        assertConexaoRejeitada(cliente, tokenExpirado(usuario));
        cliente.stop();
    }

    @Test
    void sseComAuthorizationEntregaSomenteAoUsuarioCorreto() throws Exception {
        Usuario destino = novoUsuario("destino-sse@rf23.test");
        Usuario isolado = novoUsuario("isolado-sse@rf23.test");
        HttpClient clienteHttp = HttpClient.newHttpClient();
        HttpResponse<InputStream> respostaDestino = abrirSse(
                clienteHttp, jwtService.gerarToken(destino));
        HttpResponse<InputStream> respostaIsolada = abrirSse(
                clienteHttp, jwtService.gerarToken(isolado));
        assertThat(respostaDestino.statusCode()).isEqualTo(200);
        assertThat(respostaIsolada.statusCode()).isEqualTo(200);
        BufferedReader leitorDestino = new BufferedReader(
                new InputStreamReader(respostaDestino.body(), StandardCharsets.UTF_8));
        BufferedReader leitorIsolado = new BufferedReader(
                new InputStreamReader(respostaIsolada.body(), StandardCharsets.UTF_8));
        consumirEventoConectado(leitorDestino);
        consumirEventoConectado(leitorIsolado);

        NotificacaoResponse alerta = NotificacaoResponse.builder()
                .id(77L).tipo(TipoNotificacao.CANDIDATURA).mensagem("Fallback SSE")
                .link("dashboard-contratante.html").lida(false).data(LocalDateTime.now()).build();
        long inicio = System.nanoTime();
        realtimeGateway.entregar(destino.getId(), destino.getEmail(), alerta);
        String dados = CompletableFuture.supplyAsync(() -> lerDados(leitorDestino))
                .get(5, TimeUnit.SECONDS);

        assertThat(dados).contains("\"id\":77").contains("Fallback SSE");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(5));
        Thread.sleep(250);
        assertThat(respostaIsolada.body().available()).isZero();
        respostaDestino.body().close();
        respostaIsolada.body().close();
        Thread.sleep(100);
        // Uma escrita final permite ao servidor detectar os clientes fechados e
        // encerrar os SseEmitter sem prolongar o shutdown da suíte.
        realtimeGateway.entregar(destino.getId(), destino.getEmail(), alerta);
        realtimeGateway.entregar(isolado.getId(), isolado.getEmail(), alerta);
    }

    private void assertConexaoRejeitada(WebSocketStompClient cliente, String token) {
        assertThatThrownBy(() -> conectar(cliente, token))
                .isInstanceOfAny(ExecutionException.class, IllegalStateException.class);
    }

    private StompSession conectar(WebSocketStompClient cliente, String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
        httpHeaders.setOrigin("http://localhost:3000");
        return cliente.connectAsync(
                        "ws://localhost:" + port + "/ws",
                        httpHeaders,
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private WebSocketStompClient cliente() {
        WebSocketStompClient cliente = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(new ObjectMapper().findAndRegisterModules());
        cliente.setMessageConverter(converter);
        return cliente;
    }

    private HttpResponse<InputStream> abrirSse(HttpClient cliente, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/notificacoes/stream"))
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return cliente.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    private void consumirEventoConectado(BufferedReader leitor) throws Exception {
        String linha;
        while ((linha = leitor.readLine()) != null && !linha.isBlank()) {
            // consome o evento inicial "conectado"
        }
    }

    private String lerDados(BufferedReader leitor) {
        try {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (linha.startsWith("data:")) {
                    return linha.substring(5).trim();
                }
            }
            return "";
        } catch (Exception erro) {
            throw new IllegalStateException(erro);
        }
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

    private Usuario novoUsuario(String email) {
        Usuario usuario = new Usuario();
        usuario.setNome("Pessoa WebSocket");
        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setTelefone("11999999999");
        usuario.setEmail(email);
        usuario.setSenha("{noop}teste");
        usuario.setTipoUsuario(TipoUsuario.ARTISTA);
        usuario.setPerfilCompleto(true);
        usuario.setDataCriacao(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    private String tokenExpirado(Usuario usuario) {
        return Jwts.builder()
                .subject(usuario.getEmail())
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
