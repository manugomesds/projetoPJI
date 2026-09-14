package com.portifolio.controller;

import static org.assertj.core.api.Assertions.*;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.security.JwtService;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortfolioMultipartHttpIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScripts("db/schema-test.sql","db/catalogo-test.sql").withUrlParam("stringtype","unspecified");
    @TempDir static Path root;
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("app.portfolio.storage-root",() -> root.toString());}
    @LocalServerPort int port;
    @Autowired JdbcTemplate db; @Autowired UsuarioRepository usuarios; @Autowired JwtService jwt;
    String token;
    @BeforeEach void dados() {
        long id=db.queryForObject("insert into usuarios(nome,data_nascimento,telefone,email,senha,tipo_usuario,status_conta,perfil_completo) values ('Artista','1990-01-01','11999999999',?,'hash','ARTISTA','ATIVA',false) returning id",Long.class,UUID.randomUUID()+"@http.test");
        db.update("insert into perfis_artistas(usuario_id,tipo_perfil_artistico) values (?,'ARTISTA_SOLO')",id);
        token=jwt.gerarToken(usuarios.findById(id).orElseThrow());
    }
    @AfterEach void limpar(){db.execute("truncate usuarios restart identity cascade");}
    @Test void arquivoAcima21MbRecebe413NoServidorReal() throws Exception {
        var resposta=enviar(21*1024*1024+1,1);
        assertThat(resposta.statusCode()).isEqualTo(413);assertThat(resposta.body()).contains("Envio acima do limite permitido").doesNotContain("Exception","storage-root");
        assertThat(db.queryForObject("select count(*) from portfolio_arquivos",Long.class)).isZero();
    }
    @Test void requisicaoAcima22MbRecebe413NoServidorReal() throws Exception {
        var resposta=enviar(12*1024*1024,2);assertThat(resposta.statusCode()).isEqualTo(413);
        assertThat(root.toFile().list()).isEmpty();
    }
    @Test void limiteDeNegocioAtingeServiceERetorna422() throws Exception {
        var resposta=enviar(10*1024*1024+1,1);assertThat(resposta.statusCode()).isEqualTo(422);
        assertThat(resposta.body()).contains("limite de 10 MB");assertThat(root.toFile().list()).isEmpty();
    }
    HttpResponse<String> enviar(int tamanho,int partes) throws Exception {
        String boundary="RF16Boundary"; List<byte[]> body=new ArrayList<>();
        for(int i=0;i<partes;i++) {
            body.add(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"arquivo\"; filename=\"a.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            body.add(new byte[tamanho]);body.add("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        body.add(("--"+boundary+"--\r\n").getBytes(StandardCharsets.US_ASCII));
        try(var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/portfolio/arquivos"))
                    .header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary)
                    .timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.ofByteArrays(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
    }
}
