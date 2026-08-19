package com.portifolio.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portifolio.model.Candidatura;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.ModeloTrabalho;
import com.portifolio.model.enums.StatusCandidatura;
import com.portifolio.model.enums.StatusVaga;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.CandidaturaRepository;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.repository.VagaRepository;
import com.portifolio.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// RF03 (Fase 1 + Fase 2) - teste de integracao ponta a ponta com PostgreSQL real
// via Testcontainers. H2 nao serve aqui: as colunas usam tipos enum customizados
// do Postgres (status_vaga_enum etc.), que H2 nao reconhece.
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class VagaControllerRf03IntegrationTest {

    // stringtype=unspecified e OBRIGATORIO aqui pelo mesmo motivo que esta em
    // application.properties: sem isso, o driver JDBC do Postgres rejeita valores
    // String nas colunas de enum customizado.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScript("db/schema-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @Autowired MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PerfilContratanteRepository perfilContratanteRepository;
    @Autowired PerfilArtistaRepository perfilArtistaRepository;
    @Autowired VagaRepository vagaRepository;
    @Autowired CandidaturaRepository candidaturaRepository;
    @Autowired JwtService jwtService;

    // Mesmo padrao de reset usado manualmente em dev (RESTART IDENTITY garante
    // que os IDs comecem do 1 a cada teste, o que o teste de cursor depende).
    @AfterEach
    void limparBanco() {
        jdbcTemplate.execute(
                "TRUNCATE candidaturas, vagas, perfis_artistas, perfis_contratantes, usuarios RESTART IDENTITY CASCADE");
    }

    // ---------- helpers de setup (via repository, nao via REST) ----------

    private Usuario criarUsuario(String email, TipoUsuario tipo) {
        Usuario u = new Usuario();
        u.setNome("Usuario Teste");
        u.setDataNascimento(LocalDate.of(1990, 1, 1));
        u.setTelefone("11999999999");
        u.setEmail(email);
        u.setSenha("{noop}senha-teste");
        u.setTipoUsuario(tipo);
        u.setPerfilCompleto(false);
        u.setDataCriacao(LocalDateTime.now());
        return usuarioRepository.save(u);
    }

    // @MapsId: nunca setar o ID manualmente, so a associacao com Usuario
    // (key learning ja documentado - evita merge() no lugar de persist()).
    private PerfilContratante criarContratante(Usuario usuario) {
        PerfilContratante p = new PerfilContratante();
        p.setUsuario(usuario);
        p.setNomeEmpresa("Empresa Teste");
        return perfilContratanteRepository.save(p);
    }

    private PerfilArtista criarArtista(Usuario usuario) {
        PerfilArtista p = new PerfilArtista();
        p.setUsuario(usuario);
        p.setBiografia("Biografia de teste");
        return perfilArtistaRepository.save(p);
    }

    private com.portifolio.model.Vaga criarVaga(PerfilContratante contratante, String titulo, String cidade,
                                                 ModeloTrabalho modelo, BigDecimal valor, StatusVaga status) {
        com.portifolio.model.Vaga v = new com.portifolio.model.Vaga();
        v.setContratante(contratante);
        v.setTitulo(titulo);
        v.setDescricao("Descricao de teste");
        v.setRequisitos("Requisitos de teste");
        v.setRemuneraValor(valor);
        v.setFormaPagamento("Pix");
        v.setCidade(cidade);
        v.setEstado("SP");
        v.setModeloTrabalho(modelo);
        v.setTipoContrato("Freelance");
        v.setStatus(status);
        v.setDataPublicacao(LocalDateTime.now());
        v.setTags(new HashSet<>());
        return vagaRepository.save(v);
    }

    private void criarCandidatura(com.portifolio.model.Vaga vaga, PerfilArtista artista) {
        Candidatura c = new Candidatura();
        c.setVaga(vaga);
        c.setArtista(artista);
        c.setMensagemApresentacao("Tenho interesse nesta oportunidade.");
        c.setLinkPortfolioCandidatura("https://exemplo.com/portfolio");
        c.setStatus(StatusCandidatura.PENDENTE);
        c.setDataCandidatura(LocalDateTime.now());
        candidaturaRepository.save(c);
    }

    private String tokenPara(Usuario usuario) {
        return jwtService.gerarToken(usuario);
    }

    // ---------- Fase 1: núcleo (paginação, filtros, segurança) ----------

    @Test
    void endpointDeListagemEhPublico() throws Exception {
        mockMvc.perform(get("/api/vagas"))
                .andExpect(status().isOk());
    }

    @Test
    void deveListarApenasVagasAbertasNoFeedGeral() throws Exception {
        Usuario contratanteUsuario = criarUsuario("c1@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(contratanteUsuario);

        criarVaga(contratante, "Vaga Aberta", "São Paulo", ModeloTrabalho.REMOTO, new BigDecimal("1000"), StatusVaga.ABERTA);
        criarVaga(contratante, "Vaga Pausada", "São Paulo", ModeloTrabalho.REMOTO, new BigDecimal("1000"), StatusVaga.PAUSADA);
        criarVaga(contratante, "Vaga Cancelada", "São Paulo", ModeloTrabalho.REMOTO, new BigDecimal("1000"), StatusVaga.CANCELADA);
        criarVaga(contratante, "Vaga Encerrada", "São Paulo", ModeloTrabalho.REMOTO, new BigDecimal("1000"), StatusVaga.ENCERRADA);

        mockMvc.perform(get("/api/vagas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Vaga Aberta"))
                .andExpect(jsonPath("$.content[0].status").value("ABERTA"));
    }

    @Test
    void deveBloquearCriacaoDeVagaSemToken() throws Exception {
        String corpo = """
                {
                  "contratanteId": 1, "titulo": "x", "descricao": "x", "requisitos": "x",
                  "remuneraValor": 100, "formaPagamento": "Pix", "cidade": "SP", "estado": "SP",
                  "modeloTrabalho": "REMOTO", "tipoContrato": "Freelance"
                }
                """;
        mockMvc.perform(post("/api/vagas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveFiltrarPorCidade() throws Exception {
        Usuario contratanteUsuario = criarUsuario("c2@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(contratanteUsuario);

        criarVaga(contratante, "Vaga SP", "São Paulo", ModeloTrabalho.REMOTO, new BigDecimal("1000"), StatusVaga.ABERTA);
        criarVaga(contratante, "Vaga RJ", "Rio de Janeiro", ModeloTrabalho.REMOTO, new BigDecimal("1000"), StatusVaga.ABERTA);

        mockMvc.perform(get("/api/vagas").param("cidade", "São Paulo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Vaga SP"));
    }

    @Test
    void deveFiltrarPorFaixaSalarial() throws Exception {
        Usuario contratanteUsuario = criarUsuario("c3@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(contratanteUsuario);

        criarVaga(contratante, "Barata", "SP", ModeloTrabalho.REMOTO, new BigDecimal("500"), StatusVaga.ABERTA);
        criarVaga(contratante, "Media", "SP", ModeloTrabalho.REMOTO, new BigDecimal("1500"), StatusVaga.ABERTA);
        criarVaga(contratante, "Cara", "SP", ModeloTrabalho.REMOTO, new BigDecimal("5000"), StatusVaga.ABERTA);

        mockMvc.perform(get("/api/vagas")
                        .param("faixaSalarialMin", "1000")
                        .param("faixaSalarialMax", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Media"));
    }

    @Test
    void devePaginarComCursorSemRepetirOuPular() throws Exception {
        Usuario contratanteUsuario = criarUsuario("c4@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(contratanteUsuario);

        for (int i = 1; i <= 5; i++) {
            criarVaga(contratante, "Vaga " + i, "SP", ModeloTrabalho.REMOTO, new BigDecimal("1000"), StatusVaga.ABERTA);
        }

        MvcResult pagina1 = mockMvc.perform(get("/api/vagas").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn();
        JsonNode json1 = objectMapper.readTree(pagina1.getResponse().getContentAsString());

        MvcResult pagina2 = mockMvc.perform(get("/api/vagas")
                        .param("size", "2")
                        .param("cursor", json1.get("nextCursor").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn();
        JsonNode json2 = objectMapper.readTree(pagina2.getResponse().getContentAsString());

        mockMvc.perform(get("/api/vagas")
                        .param("size", "2")
                        .param("cursor", json2.get("nextCursor").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));

        Set<String> titulosPagina1 = extrairTitulos(json1);
        Set<String> titulosPagina2 = extrairTitulos(json2);
        assertThat(java.util.Collections.disjoint(titulosPagina1, titulosPagina2)).isTrue();
    }

    private Set<String> extrairTitulos(JsonNode pagina) {
        Set<String> titulos = new HashSet<>();
        pagina.get("content").forEach(v -> titulos.add(v.get("titulo").asText()));
        return titulos;
    }

    @Test
    void deveLimitarTamanhoDaPaginaAoMaximoPermitido() throws Exception {
        Usuario contratanteUsuario = criarUsuario("c5@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(contratanteUsuario);

        for (int i = 1; i <= 55; i++) {
            criarVaga(contratante, "Vaga " + i, "SP", ModeloTrabalho.REMOTO, new BigDecimal("1000"), StatusVaga.ABERTA);
        }

        mockMvc.perform(get("/api/vagas").param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(50))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void deveRetornar400ParaModeloTrabalhoInvalido() throws Exception {
        mockMvc.perform(get("/api/vagas").param("modeloTrabalho", "VOADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ---------- Fase 2: vagas CANCELADA visíveis só pra quem se candidatou ----------

    @Test
    void artistaComCandidaturaEmVagaCanceladaDeveVeLaEmSecaoSeparada() throws Exception {
        Usuario contratanteUsuario = criarUsuario("c6@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(contratanteUsuario);

        Usuario artistaUsuario = criarUsuario("a1@teste.com", TipoUsuario.ARTISTA);
        PerfilArtista artista = criarArtista(artistaUsuario);

        var vagaCancelada = criarVaga(contratante, "Vaga Cancelada", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.CANCELADA);
        criarCandidatura(vagaCancelada, artista);

        String token = tokenPara(artistaUsuario);

        mockMvc.perform(get("/api/vagas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.vagasCanceladasComCandidatura.length()").value(1))
                .andExpect(jsonPath("$.vagasCanceladasComCandidatura[0].id").value(vagaCancelada.getId()))
                .andExpect(jsonPath("$.vagasCanceladasComCandidatura[0].cancelada").value(true));
    }

    @Test
    void semTokenNaoDeveExporVagaCanceladaDeNinguem() throws Exception {
        Usuario contratanteUsuario = criarUsuario("c7@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(contratanteUsuario);

        Usuario artistaUsuario = criarUsuario("a2@teste.com", TipoUsuario.ARTISTA);
        PerfilArtista artista = criarArtista(artistaUsuario);

        var vagaCancelada = criarVaga(contratante, "Vaga Cancelada", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.CANCELADA);
        criarCandidatura(vagaCancelada, artista);

        mockMvc.perform(get("/api/vagas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vagasCanceladasComCandidatura.length()").value(0));
    }

    @Test
    void artistaSemCandidaturaNaoVeVagaCanceladaDeOutroArtista() throws Exception {
        Usuario contratanteUsuario = criarUsuario("c8@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(contratanteUsuario);

        Usuario artistaComCandidatura = criarUsuario("a3@teste.com", TipoUsuario.ARTISTA);
        PerfilArtista perfilComCandidatura = criarArtista(artistaComCandidatura);

        Usuario artistaSemCandidatura = criarUsuario("a4@teste.com", TipoUsuario.ARTISTA);
        criarArtista(artistaSemCandidatura);

        var vagaCancelada = criarVaga(contratante, "Vaga Cancelada", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.CANCELADA);
        criarCandidatura(vagaCancelada, perfilComCandidatura);

        String tokenOutroArtista = tokenPara(artistaSemCandidatura);

        mockMvc.perform(get("/api/vagas").header("Authorization", "Bearer " + tokenOutroArtista))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vagasCanceladasComCandidatura.length()").value(0));
    }

    @Test
    void contratanteLogadoNuncaVeSecaoDeVagasCanceladas() throws Exception {
        Usuario contratanteUsuario = criarUsuario("c9@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(contratanteUsuario);

        Usuario artistaUsuario = criarUsuario("a5@teste.com", TipoUsuario.ARTISTA);
        PerfilArtista artista = criarArtista(artistaUsuario);

        var vagaCancelada = criarVaga(contratante, "Vaga Cancelada", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.CANCELADA);
        criarCandidatura(vagaCancelada, artista);

        String tokenContratante = tokenPara(contratanteUsuario);

        mockMvc.perform(get("/api/vagas").header("Authorization", "Bearer " + tokenContratante))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vagasCanceladasComCandidatura.length()").value(0));
    }

    @Test
    void contratanteDeveExecutarCrudCompletoDaPropriaVaga() throws Exception {
        Usuario usuario = criarUsuario("crud-vaga@teste.com", TipoUsuario.CONTRATANTE);
        criarContratante(usuario);
        String token = tokenPara(usuario);

        String criacao = """
                {
                  "contratanteId": %d,
                  "titulo": "Fotografo de Evento",
                  "descricao": "Cobertura completa do evento",
                  "requisitos": "Portfolio atualizado",
                  "remuneraValor": 750.00,
                  "formaPagamento": "Pix",
                  "cidade": "Sao Paulo",
                  "estado": "SP",
                  "modeloTrabalho": "PRESENCIAL",
                  "tipoContrato": "Freelance",
                  "categoria": "Fotografia",
                  "experiencia": "Intermediaria",
                  "dataLimiteCandidatura": "2030-05-05",
                  "abrangencia": "regional",
                  "fotos": ["https://example.com/foto.jpg"]
                }
                """.formatted(usuario.getId());

        MvcResult criado = mockMvc.perform(post("/api/vagas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(criacao))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Fotografo de Evento"))
                .andReturn();
        long vagaId = objectMapper.readTree(criado.getResponse().getContentAsString()).get("id").asLong();

        String atualizacao = criacao.replace("Fotografo de Evento", "Fotografo Atualizado");
        mockMvc.perform(put("/api/vagas/{id}", vagaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Fotografo Atualizado"));

        mockMvc.perform(get("/api/vagas/{id}", vagaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("Fotografia"));

        mockMvc.perform(delete("/api/vagas/{id}", vagaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(vagaRepository.findById(vagaId).orElseThrow().getStatus()).isEqualTo(StatusVaga.CANCELADA);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from log_vagas_canceladas where vaga_id = ?", Integer.class, vagaId)).isEqualTo(1);
    }
}
