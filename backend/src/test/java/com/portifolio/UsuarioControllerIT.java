package com.portifolio;

import com.portifolio.dto.CadastroRequest;
import com.portifolio.dto.LoginRequest;
import com.portifolio.dto.UsuarioAtualizacaoRequest;
import com.portifolio.model.enums.TipoUsuario;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UsuarioControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScript("db/schema-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    void deveExecutarCrudDaPropriaConta() {
        String email = "teste-" + UUID.randomUUID() + "@example.com";
        String baseUrl = "http://localhost:" + port;

        CadastroRequest cadastro = new CadastroRequest();
        cadastro.setNome("Usuário Teste");
        cadastro.setDataNascimento(LocalDate.of(1990, 1, 1));
        cadastro.setTelefone("11999999999");
        cadastro.setEmail(email);
        cadastro.setSenha("senha123");
        cadastro.setTipoUsuario(TipoUsuario.CONTRATANTE);
        cadastro.setTipoPerfilContratante("Pessoa Física");

        ResponseEntity<Map> criado = restTemplate.postForEntity(
                baseUrl + "/api/auth/cadastro", cadastro, Map.class);
        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setSenha("senha123");
        ResponseEntity<Map> autenticado = restTemplate.postForEntity(
                baseUrl + "/api/auth/login", login, Map.class);
        assertThat(autenticado.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = String.valueOf(autenticado.getBody().get("token"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> consultado = restTemplate.exchange(
                baseUrl + "/api/usuarios/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(consultado.getBody().get("email")).isEqualTo(email);

        UsuarioAtualizacaoRequest atualizacao = new UsuarioAtualizacaoRequest();
        atualizacao.setNome("Usuário Atualizado");
        atualizacao.setDataNascimento(LocalDate.of(1990, 1, 1));
        atualizacao.setTelefone("11988888888");
        atualizacao.setEmail(email);
        ResponseEntity<Map> atualizado = restTemplate.exchange(
                baseUrl + "/api/usuarios/me", HttpMethod.PUT,
                new HttpEntity<>(atualizacao, headers), Map.class);
        assertThat(atualizado.getBody().get("nome")).isEqualTo("Usuário Atualizado");

        ResponseEntity<Void> excluido = restTemplate.exchange(
                baseUrl + "/api/usuarios/me", HttpMethod.DELETE,
                new HttpEntity<>(headers), Void.class);
        assertThat(excluido.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
