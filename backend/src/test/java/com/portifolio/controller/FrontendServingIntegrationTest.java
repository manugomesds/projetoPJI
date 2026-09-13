package com.portifolio.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FrontendServingIntegrationTest {

    private static final String REACT_ROOT = "<div id=\"root\"></div>";
    private static final Pattern MAIN_SCRIPT = Pattern.compile("src=\"(/static/js/main\\.[^\"]+\\.js)\"");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScripts("db/schema-test.sql", "db/catalogo-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @LocalServerPort
    int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void raizServeIndexReact() throws Exception {
        Resposta resposta = get("/");

        assertThat(resposta.status()).isEqualTo(200);
        assertThat(resposta.contentType()).contains("text/html");
        assertThat(resposta.body()).contains(REACT_ROOT);
    }

    @Test
    void rotasReactDeVagasRecebemOMesmoIndex() throws Exception {
        String index = get("/").body();

        for (String rota : new String[]{
                "/vagas", "/vagas/", "/vagas/1", "/vagas/999",
                "/vagas/nova", "/vagas/1/gerenciar", "/vagas/1/editar",
                "/minhas-vagas"}) {
            Resposta resposta = get(rota);
            assertThat(resposta.status()).as(rota).isEqualTo(200);
            assertThat(resposta.body()).as(rota).isEqualTo(index);
        }
    }

    @Test
    void rotasReactDoRf09RecebemOMesmoIndex() throws Exception {
        String index = get("/").body();

        for (String rota : new String[]{"/recuperar-senha", "/redefinir-senha"}) {
            Resposta resposta = get(rota);
            assertThat(resposta.status()).as(rota).isEqualTo(200);
            assertThat(resposta.body()).as(rota).isEqualTo(index);
        }
    }

    @Test
    void rotasReactDeAutenticacaoRecebemOMesmoIndex() throws Exception {
        String index = get("/").body();

        for (String rota : new String[]{"/login", "/cadastro"}) {
            Resposta resposta = get(rota);
            assertThat(resposta.status()).as(rota).isEqualTo(200);
            assertThat(resposta.body()).as(rota).isEqualTo(index);
        }
    }

    @Test
    void rotasReactDaContaRecebemOMesmoIndex() throws Exception {
        String index = get("/").body();

        for (String rota : new String[]{"/dashboard", "/perfil", "/mensagens"}) {
            Resposta resposta = get(rota);
            assertThat(resposta.status()).as(rota).isEqualTo(200);
            assertThat(resposta.body()).as(rota).isEqualTo(index);
        }
    }

    @Test
    void rotasReactDePerfisRecebemOMesmoIndex() throws Exception {
        String index = get("/").body();

        for (String rota : new String[]{
                "/perfis", "/perfis/", "/perfis/ARTISTA/1", "/perfis/CONTRATANTE/1"}) {
            Resposta resposta = get(rota);
            assertThat(resposta.status()).as(rota).isEqualTo(200);
            assertThat(resposta.body()).as(rota).isEqualTo(index);
        }
    }

    @Test
    void paginasLegadasContinuamLiterais() throws Exception {
        for (String pagina : new String[]{
                "/home.html",
                "/buscar-vagas.html",
                "/login.html",
                "/cadastro-contratante.html",
                "/detalhe-vaga.html",
                "/minhas-vagas.html",
                "/publicar-vaga.html",
                "/detalhe-vaga-proprietario.html",
                "/editar-vagas.html",
                "/editar-vagas-2.html",
                "/confirmar-exclusao-vaga.html",
                "/dashboard-contratante.html",
                "/perfil.html",
                "/perfil-publico.html",
                "/mensagens.html",
                "/recuperar-senha.html",
                "/redefinir-senha.html"}) {
            Resposta resposta = get(pagina);
            assertThat(resposta.status()).as(pagina).isEqualTo(200);
            assertThat(resposta.contentType()).as(pagina).contains("text/html");
            assertThat(resposta.body()).as(pagina).doesNotContain(REACT_ROOT);
        }
    }

    @Test
    void bundleReactRealEEntregue() throws Exception {
        Matcher matcher = MAIN_SCRIPT.matcher(get("/").body());
        assertThat(matcher.find()).isTrue();

        Resposta resposta = get(matcher.group(1));
        assertThat(resposta.status()).isEqualTo(200);
        assertThat(resposta.contentType()).contains("javascript");
        assertThat(resposta.body()).isNotBlank();
    }

    @Test
    void recursoLegadoRealEEntregue() throws Exception {
        Resposta resposta = get("/css/base.css");

        assertThat(resposta.status()).isEqualTo(200);
        assertThat(resposta.contentType()).contains("text/css");
        assertThat(resposta.body()).contains("--");
    }

    @Test
    void assetInexistenteRetorna404Real() throws Exception {
        Resposta resposta = get("/static/js/arquivo-inexistente.js");

        assertThat(resposta.status()).isEqualTo(404);
        assertThat(resposta.body()).doesNotContain(REACT_ROOT);
    }

    @Test
    void caminhoDesconhecidoRetorna404Real() throws Exception {
        Resposta resposta = get("/rota-inexistente");

        assertThat(resposta.status()).isEqualTo(404);
        assertThat(resposta.body()).doesNotContain(REACT_ROOT);
    }

    @Test
    void apiContinuaApiENaoRecebeHtmlReact() throws Exception {
        Resposta vagaInexistente = get("/api/vagas/1");
        assertThat(vagaInexistente.status()).isEqualTo(404);
        assertThat(vagaInexistente.contentType()).contains("application/json");
        assertThat(vagaInexistente.body()).doesNotContain(REACT_ROOT);

        Resposta apiProtegida = get("/api/recurso-inexistente");
        assertThat(apiProtegida.status()).isEqualTo(401);
        assertThat(apiProtegida.body()).doesNotContain(REACT_ROOT);
    }

    @Test
    void websocketNaoRecebeHtmlReact() throws Exception {
        Resposta resposta = get("/ws");

        assertThat(resposta.status()).isNotEqualTo(200);
        assertThat(resposta.body()).doesNotContain(REACT_ROOT);
    }

    private Resposta get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new Resposta(
                response.statusCode(),
                response.headers().firstValue("content-type").orElse(""),
                response.body());
    }

    private record Resposta(int status, String contentType, String body) {
    }
}
