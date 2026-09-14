package com.portifolio.controller;

import com.portifolio.repository.AreaArtisticaRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.security.JwtService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AreaArtisticaIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScripts("db/schema-test.sql", "db/catalogo-test.sql")
            .withUrlParam("stringtype", "unspecified");
    @Autowired MockMvc mvc;
    @Autowired AreaArtisticaRepository areas;
    @Autowired UsuarioRepository usuarios;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate db;

    @Test void catalogoPublicoSomenteIdNomeEmOrdemEstavel() throws Exception {
        var expected = areas.findAll(Sort.by("id")).stream()
                .map(a -> Map.of("id", a.getId(), "nome", a.getNome())).toList();
        assertThat(expected).isNotEmpty();
        mvc.perform(get("/api/areas")).andExpect(status().isOk())
                .andExpect(content().json(new ObjectMapper().writeValueAsString(expected)));
        var body = new ObjectMapper().readTree(mvc.perform(get("/api/areas")).andReturn().getResponse().getContentAsString());
        body.forEach(item -> assertThat(item.size()).isEqualTo(2));
    }

    @ParameterizedTest @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void escritaNaoExpostaMesmoParaContratante(String method) throws Exception {
        long count = areas.count();
        mvc.perform(request(HttpMethod.valueOf(method), "/api/areas")).andExpect(status().isUnauthorized());
        mvc.perform(request(HttpMethod.valueOf(method), "/api/areas").header("Authorization", bearer("CONTRATANTE")))
                .andExpect(status().isForbidden());
        assertThat(areas.count()).isEqualTo(count);
    }

    @Test void rf13ContinuaRestrito() throws Exception {
        mvc.perform(get("/api/talentos/areas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/talentos/areas").header("Authorization", bearer("ARTISTA"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/talentos/areas").header("Authorization", bearer("CONTRATANTE"))).andExpect(status().isOk());
        mvc.perform(get("/api/areas/1")).andExpect(status().isUnauthorized());
    }

    private String bearer(String tipo) {
        Long id = db.queryForObject("""
                insert into usuarios(nome,email,senha,tipo_usuario,status_conta,data_nascimento,perfil_completo,telefone)
                values ('Catálogo',?,'hash',?,'ATIVA','1990-01-01',true,'11999999999') returning id
                """, Long.class, UUID.randomUUID() + "@catalogo.test", tipo);
        return "Bearer " + jwt.gerarToken(usuarios.findById(id).orElseThrow());
    }
}
