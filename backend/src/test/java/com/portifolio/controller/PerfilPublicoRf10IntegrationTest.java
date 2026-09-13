package com.portifolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Funcao;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.FuncaoRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.security.JwtService;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.HashSet;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PerfilPublicoRf10IntegrationTest {

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
    @Autowired FuncaoRepository funcaoRepository;
    @Autowired JwtService jwtService;
    @Autowired EntityManagerFactory entityManagerFactory;

    @AfterEach
    void limparBanco() {
        jdbcTemplate.execute(
                "TRUNCATE funcoes, perfis_artistas, perfis_contratantes, usuarios RESTART IDENTITY CASCADE");
    }

    @Test
    void visitanteVisualizaArtistaAdultoSomenteComListaBranca() throws Exception {
        PerfilArtista perfil = novoArtista("artista-publico@teste.com", LocalDate.of(1994, 2, 10), false);
        perfil.setBiografia("Atriz e diretora.");
        perfil.setLocalizacao("Campinas - SP");
        perfil.setUrlPortfolio("https://portfolio.example/artista");
        perfil.setBannerUrl("https://cdn.example/banner.jpg");
        perfil.getUsuario().setFotoPerfil("https://cdn.example/avatar-custom.jpg");
        perfil.getUsuario().setNomeResponsavel("Dado sigiloso");
        perfil.getUsuario().setTelefoneResponsavel("11900000000");
        perfil.getUsuario().setEmailResponsavel("responsavel@example.test");
        perfil.getUsuario().setTokenRecuperacao("token-sigiloso");
        usuarioRepository.save(perfil.getUsuario());
        perfil.getAreas().iterator().next().getFuncoes().add(novaFuncao("Teatro"));
        perfil.getAreas().iterator().next().getFuncoes().add(novaFuncao("Cinema"));
        perfilArtistaRepository.save(perfil);

        mockMvc.perform(get("/api/perfis/publicos/ARTISTA/{id}", perfil.getUsuarioId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(perfil.getUsuarioId()))
                .andExpect(jsonPath("$.nomeExibicao").value("Pessoa RF10"))
                .andExpect(jsonPath("$.biografia").value("Atriz e diretora."))
                .andExpect(jsonPath("$.localizacao").value("Campinas - SP"))
                .andExpect(jsonPath("$.urlPortfolio").value("https://portfolio.example/artista"))
                .andExpect(jsonPath("$.bannerUrl").value("https://cdn.example/banner.jpg"))
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.example/avatar-custom.jpg"))
                .andExpect(jsonPath("$.funcoes[0].nome").value("Cinema"))
                .andExpect(jsonPath("$.funcoes[1].nome").value("Teatro"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefone").doesNotExist())
                .andExpect(jsonPath("$.dataNascimento").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.googleId").doesNotExist())
                .andExpect(jsonPath("$.tokenRecuperacao").doesNotExist())
                .andExpect(jsonPath("$.tokenExpiracao").doesNotExist())
                .andExpect(jsonPath("$.nomeResponsavel").doesNotExist())
                .andExpect(jsonPath("$.telefoneResponsavel").doesNotExist())
                .andExpect(jsonPath("$.emailResponsavel").doesNotExist())
                .andExpect(jsonPath("$.perfilCompleto").doesNotExist())
                .andExpect(jsonPath("$.nivelMedalha").doesNotExist())
                .andExpect(jsonPath("$.scoreEngajamento").doesNotExist());
    }

    @Test
    void usuarioAutenticadoTambemVisualizaPerfilPublico() throws Exception {
        PerfilArtista perfil = novoArtista("artista-autenticado@teste.com", LocalDate.of(1990, 1, 1), true);
        perfil.getUsuario().setStatusConta(com.portifolio.model.enums.StatusConta.ATIVA);
        usuarioRepository.saveAndFlush(perfil.getUsuario());

        mockMvc.perform(get("/api/perfis/publicos/ARTISTA/{id}", perfil.getUsuarioId())
                        .header("Authorization", "Bearer " + jwtService.gerarToken(perfil.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao").value("Pessoa RF10"));
    }

    @Test
    void visitanteVisualizaContratanteComNomeDaEmpresa() throws Exception {
        PerfilContratante perfil = novoContratante(
                "contratante-publico@teste.com", LocalDate.of(1988, 5, 4), false);
        perfil.setNomeEmpresa("Estudio Palco");
        perfil.setTipoPerfil("Produtora");
        perfil.setBiografia("Producoes culturais.");
        perfil.setLocalizacao("Sao Paulo - SP");
        perfil.setBannerUrl("https://cdn.example/empresa-banner.jpg");
        perfilContratanteRepository.save(perfil);

        mockMvc.perform(get("/api/perfis/publicos/CONTRATANTE/{id}", perfil.getUsuarioId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao").value("Estudio Palco"))
                .andExpect(jsonPath("$.nomeEmpresa").value("Estudio Palco"))
                .andExpect(jsonPath("$.tipoPerfil").value("Produtora"))
                .andExpect(jsonPath("$.bannerUrl").value("https://cdn.example/empresa-banner.jpg"))
                .andExpect(jsonPath("$.avatarUrl").isNotEmpty())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefone").doesNotExist())
                .andExpect(jsonPath("$.dataNascimento").doesNotExist());
    }

    @Test
    void contratanteSemEmpresaUsaNomeDoUsuario() throws Exception {
        PerfilContratante perfil = novoContratante(
                "contratante-fallback@teste.com", LocalDate.of(1985, 6, 15), true);
        perfil.setNomeEmpresa("   ");
        perfilContratanteRepository.save(perfil);

        mockMvc.perform(get("/api/perfis/publicos/CONTRATANTE/{id}", perfil.getUsuarioId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao").value("Pessoa RF10"));
    }

    @Test
    void perfilAdultoIncompletoPermanecePublico() throws Exception {
        PerfilArtista perfil = novoArtista("adulto-incompleto@teste.com", LocalDate.of(1999, 1, 1), false);

        mockMvc.perform(get("/api/perfis/publicos/ARTISTA/{id}", perfil.getUsuarioId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biografia").doesNotExist())
                .andExpect(jsonPath("$.funcoes.length()").value(0));
    }

    @Test
    void artistaMenorFicaPrivadoPorPadraoSemVazarDados() throws Exception {
        PerfilArtista perfil = novoArtista(
                "menor-artista@teste.com", LocalDate.now().minusYears(16), true);
        perfil.getUsuario().setNomeResponsavel("Responsavel privado");
        perfil.getUsuario().setTelefoneResponsavel("11911111111");
        perfil.getUsuario().setEmailResponsavel("privado@example.test");
        usuarioRepository.save(perfil.getUsuario());

        mockMvc.perform(get("/api/perfis/publicos/ARTISTA/{id}", perfil.getUsuarioId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.nomeResponsavel").doesNotExist());
    }

    @Test
    void contratanteMenorTambemFicaPrivadoPorPadrao() throws Exception {
        PerfilContratante perfil = novoContratante(
                "menor-contratante@teste.com", LocalDate.now().minusYears(17), true);

        mockMvc.perform(get("/api/perfis/publicos/CONTRATANTE/{id}", perfil.getUsuarioId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void perfilInexistenteOuTipoIncorretoRetorna404() throws Exception {
        PerfilArtista artista = novoArtista("tipo-incorreto@teste.com", LocalDate.of(1992, 1, 1), true);

        mockMvc.perform(get("/api/perfis/publicos/ARTISTA/999999"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/perfis/publicos/CONTRATANTE/999999"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/perfis/publicos/CONTRATANTE/{id}", artista.getUsuarioId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void tipoInvalidoRetorna400NoFormatoPadrao() throws Exception {
        mockMvc.perform(get("/api/perfis/publicos/INEXISTENTE/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value("Parâmetro 'tipo' possui valor inválido."));
    }

    @Test
    void avatarFallbackEhDeterministicoOpacoENaoEhPersistido() throws Exception {
        PerfilArtista perfil = novoArtista("avatar-fallback@teste.com", LocalDate.of(1990, 1, 1), true);

        String primeira = mockMvc.perform(get("/api/perfis/publicos/ARTISTA/{id}", perfil.getUsuarioId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String segunda = mockMvc.perform(get("/api/perfis/publicos/ARTISTA/{id}", perfil.getUsuarioId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(primeira).isEqualTo(segunda);
        assertThat(primeira).containsPattern("seed=[0-9a-f]{64}");
        assertThat(primeira).doesNotContain("seed=" + perfil.getUsuarioId() + "\"");
        assertThat(jdbcTemplate.queryForObject(
                "select foto_perfil_url is null from usuarios where id = ?", Boolean.class, perfil.getUsuarioId()))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select not exists (select 1 from information_schema.columns where table_name='perfis_artistas' and column_name='foto_perfil') from perfis_artistas where usuario_id = ?",
                Boolean.class, perfil.getUsuarioId())).isTrue();
    }

    @Test
    void artistaComMuitasFuncoesNaoDisparaNMaisUm() throws Exception {
        PerfilArtista perfil = novoArtista("sem-n-mais-um@teste.com", LocalDate.of(1991, 1, 1), true);
        IntStream.range(0, 20).forEach(numero -> perfil.getAreas().iterator().next().getFuncoes().add(novaFuncao("Funcao RF10 " + numero)));
        perfilArtistaRepository.save(perfil);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/perfis/publicos/ARTISTA/{id}", perfil.getUsuarioId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funcoes.length()").value(20));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    @Test
    void liberacaoPublicaNaoAbreRotasPrivadasDePerfil() throws Exception {
        PerfilArtista perfil = novoArtista("regressao-seguranca@teste.com", LocalDate.of(1993, 1, 1), true);

        mockMvc.perform(get("/api/perfis-artistas/{id}", perfil.getUsuarioId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/perfis-artistas/{id}", perfil.getUsuarioId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private PerfilArtista novoArtista(String email, LocalDate nascimento, boolean completo) {
        Usuario usuario = novoUsuario(email, nascimento, TipoUsuario.ARTISTA, completo);
        PerfilArtista perfil = new PerfilArtista();
        perfil.setTipoPerfilArtistico(com.portifolio.model.enums.TipoPerfilArtistico.ARTISTA_SOLO);
        perfil.setRaioAtuacao(com.portifolio.model.enums.Abrangencia.LOCAL);
        perfil.setUsuario(usuario);
        com.portifolio.support.OfficialSchemaFixtures.funcoes(perfil, new HashSet<>());
        return perfilArtistaRepository.save(perfil);
    }

    private PerfilContratante novoContratante(String email, LocalDate nascimento, boolean completo) {
        Usuario usuario = novoUsuario(email, nascimento, TipoUsuario.CONTRATANTE, completo);
        PerfilContratante perfil = new PerfilContratante();
        perfil.setUsuario(usuario);
        return perfilContratanteRepository.save(perfil);
    }

    private Usuario novoUsuario(String email, LocalDate nascimento, TipoUsuario tipo, boolean completo) {
        Usuario usuario = new Usuario();
        usuario.setNome("Pessoa RF10");
        usuario.setDataNascimento(nascimento);
        usuario.setTelefone("11999998888");
        usuario.setEmail(email);
        usuario.setSenha("hash-nao-publico");
        usuario.setTipoUsuario(tipo);
        usuario.setPerfilCompleto(completo);
        usuario.setGoogleId("google-" + email);
        return usuarioRepository.save(usuario);
    }

    private Funcao novaFuncao(String nome) {
        Funcao funcao = new Funcao();
        funcao.setArea(com.portifolio.support.OfficialSchemaFixtures.area());
        funcao.setNome(nome);
        return funcaoRepository.save(funcao);
    }
}
