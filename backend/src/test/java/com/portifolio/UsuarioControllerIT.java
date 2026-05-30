package com.portifolio;

import com.portifolio.dto.UsuarioRequest;
import com.portifolio.dto.UsuarioResponse;
import com.portifolio.model.enums.TipoUsuario;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UsuarioControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void deveCriarEBuscarUsuario() {
        UsuarioRequest request = new UsuarioRequest();
        request.setNome("Usuário Teste");
        request.setDataNascimento(LocalDate.of(1990, 1, 1));
        request.setTelefone("11999999999");
        request.setEmail("teste-" + UUID.randomUUID() + "@example.com");
        request.setSenha("senha123");
        request.setTipoUsuario(TipoUsuario.ARTISTA);

        ResponseEntity<UsuarioResponse> created = restTemplate.postForEntity("/api/usuarios", request, UsuarioResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        Long id = created.getBody().getId();

        ResponseEntity<UsuarioResponse> fetched = restTemplate.getForEntity("/api/usuarios/" + id, UsuarioResponse.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).isNotNull();
        assertThat(fetched.getBody().getEmail()).isEqualTo(request.getEmail());
    }
}
