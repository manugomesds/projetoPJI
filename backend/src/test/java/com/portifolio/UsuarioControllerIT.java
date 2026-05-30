package com.portifolio;

import com.portifolio.dto.UsuarioRequest;
import com.portifolio.dto.UsuarioResponse;
import com.portifolio.model.enums.TipoUsuario;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UsuarioControllerIT {

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    void deveCriarEBuscarUsuario() {
        UsuarioRequest request = new UsuarioRequest();
        request.setNome("Usuário Teste");
        request.setDataNascimento(LocalDate.of(1990, 1, 1));
        request.setTelefone("11999999999");
        request.setEmail("teste-" + UUID.randomUUID() + "@example.com");
        request.setSenha("senha123");
        request.setTipoUsuario(TipoUsuario.ARTISTA);

        String baseUrl = "http://localhost:" + port;
        ResponseEntity<UsuarioResponse> created = restTemplate.postForEntity(baseUrl + "/api/usuarios", request, UsuarioResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        Long id = created.getBody().getId();

        ResponseEntity<UsuarioResponse> fetched = restTemplate.getForEntity(baseUrl + "/api/usuarios/" + id, UsuarioResponse.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).isNotNull();
        assertThat(fetched.getBody().getEmail()).isEqualTo(request.getEmail());
    }
}
