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
            .withInitScripts("db/schema-test.sql", "db/catalogo-test.sql")
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
                "TRUNCATE candidaturas, vagas, funcoes, perfis_artistas, perfis_contratantes, usuarios RESTART IDENTITY CASCADE");
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
        p.setTipoPerfilArtistico(com.portifolio.model.enums.TipoPerfilArtistico.ARTISTA_SOLO);
        p.setRaioAtuacao(com.portifolio.model.enums.Abrangencia.LOCAL);
        p.setUsuario(usuario);
        p.setBiografia("Biografia de teste");
        return perfilArtistaRepository.save(p);
    }

    @Test
    void filtroLegadoNaoPodeSerIgnoradoOuReinterpretadoComoFuncao() throws Exception {
        mockMvc.perform(get("/api/vagas").param("tagIds", "1"))
                .andExpect(status().isUnprocessableEntity());
    }

    private com.portifolio.model.Vaga criarVaga(PerfilContratante contratante, String titulo, String cidade,
                                                 ModeloTrabalho modelo, BigDecimal valor, StatusVaga status) {
        com.portifolio.model.Vaga v = new com.portifolio.model.Vaga();
        v.setArea(com.portifolio.support.OfficialSchemaFixtures.area());
        v.setAbrangencia(com.portifolio.model.enums.Abrangencia.LOCAL);
        v.setContratante(contratante);
        v.setTitulo(titulo);
        v.setDescricao("Descricao de teste");
        v.setRequisitos("Requisitos de teste");
        v.setValorMinimo(valor);
        v.setValorMaximo(valor);
        v.setFormaRemuneracao(com.portifolio.model.enums.FormaRemuneracao.POR_EVENTO);
        v.setCidade(cidade);
        v.setEstado("SP");
        v.setModeloTrabalho(modelo);
        v.setTipoContrato("Freelance");
        v.setStatus(status);
        v.setDataPublicacao(LocalDateTime.now());
        v.setFuncoes(new HashSet<>());
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
                  "areaId": 1, "abrangencia": "LOCAL", "valorMinimo": 100, "valorMaximo": 100, "formaRemuneracao": "POR_EVENTO", "cidade": "SP", "estado": "SP",
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

    @Test
    void deveFiltrarPorTodosOsCamposCompativeisComOModelo() throws Exception {
        Usuario usuarioAlvo = criarUsuario("filtros-alvo@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratanteAlvo = criarContratante(usuarioAlvo);
        contratanteAlvo.setNomeEmpresa("Palco Cultural");
        perfilContratanteRepository.save(contratanteAlvo);

        Usuario usuarioOutro = criarUsuario("filtros-outro@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratanteOutro = criarContratante(usuarioOutro);
        contratanteOutro.setNomeEmpresa("Outra Empresa");
        perfilContratanteRepository.save(contratanteOutro);

        var alvo = criarVaga(contratanteAlvo, "Guitarrista Jazz", "Campinas",
                ModeloTrabalho.HIBRIDO, new BigDecimal("2500"), StatusVaga.ABERTA);
        alvo.setEstado("SP");
        alvo.setTipoContrato("Temporário");
        alvo.setArea(com.portifolio.support.OfficialSchemaFixtures.area((short) 1));
        vagaRepository.save(alvo);

        var outro = criarVaga(contratanteOutro, "Fotógrafo", "Niterói",
                ModeloTrabalho.PRESENCIAL, new BigDecimal("900"), StatusVaga.ABERTA);
        outro.setEstado("RJ");
        outro.setTipoContrato("Freelance");
        outro.setArea(com.portifolio.support.OfficialSchemaFixtures.area((short) 2));
        vagaRepository.save(outro);

        Long funcaoAlvo = jdbcTemplate.queryForObject(
                "insert into funcoes(area_id, nome) values (1, ?) returning id", Long.class, "Jazz");
        Long funcaoOutra = jdbcTemplate.queryForObject(
                "insert into funcoes(area_id, nome) values (1, ?) returning id", Long.class, "Retrato");
        jdbcTemplate.update("insert into vaga_funcao (vaga_id, funcao_id) values (?, ?)", alvo.getId(), funcaoAlvo);
        jdbcTemplate.update("insert into vaga_funcao (vaga_id, funcao_id) values (?, ?)", outro.getId(), funcaoOutra);

        String[][] filtros = {
                {"titulo", "guitarrista"},
                {"empresa", "palco cultural"},
                {"cidade", "Campinas"},
                {"estado", "sp"},
                {"modeloTrabalho", "HIBRIDO"},
                {"tipoContrato", "temporário"},
                {"faixaSalarialMin", "2000"},
                {"areaAtuacao", "música"},
                {"funcaoIds", funcaoAlvo.toString()}
        };

        for (String[] filtro : filtros) {
            mockMvc.perform(get("/api/vagas").param(filtro[0], filtro[1]))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(alvo.getId()));
        }

        mockMvc.perform(get("/api/vagas")
                        .param("empresa", "Palco")
                        .param("cidade", "Campinas")
                        .param("modeloTrabalho", "HIBRIDO")
                        .param("areaAtuacao", "Música")
                        .param("funcaoIds", funcaoAlvo.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(alvo.getId()));
    }

    @Test
    void filtrosOmitidosNaoDevemRestringirResultados() throws Exception {
        Usuario usuario = criarUsuario("sem-filtros@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(usuario);
        criarVaga(contratante, "Primeira", "São Paulo", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.ABERTA);
        criarVaga(contratante, "Segunda", "Recife", ModeloTrabalho.PRESENCIAL,
                new BigDecimal("2000"), StatusVaga.ABERTA);

        mockMvc.perform(get("/api/vagas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void deveUsarPadraoVinteEInformarUltimaPagina() throws Exception {
        Usuario usuario = criarUsuario("paginacao-padrao@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(usuario);
        for (int i = 1; i <= 25; i++) {
            criarVaga(contratante, "Vaga padrão " + i, "SP", ModeloTrabalho.REMOTO,
                    new BigDecimal("1000"), StatusVaga.ABERTA);
        }

        MvcResult primeira = mockMvc.perform(get("/api/vagas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn();
        JsonNode jsonPrimeira = objectMapper.readTree(primeira.getResponse().getContentAsString());

        MvcResult ultima = mockMvc.perform(get("/api/vagas")
                        .param("cursor", jsonPrimeira.get("nextCursor").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn();
        JsonNode jsonUltima = objectMapper.readTree(ultima.getResponse().getContentAsString());

        assertThat(jsonUltima.get("nextCursor").isNull()).isTrue();
        assertThat(java.util.Collections.disjoint(
                extrairTitulos(jsonPrimeira), extrairTitulos(jsonUltima))).isTrue();

        mockMvc.perform(get("/api/vagas").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20));
    }

    @Test
    void deveRejeitarFiltrosEstruturalmenteInvalidos() throws Exception {
        mockMvc.perform(get("/api/vagas").param("cursor", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/vagas").param("cursorCanceladas", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/vagas").param("estado", "S"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/vagas").param("faixaSalarialMin", "-0.01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/vagas")
                        .param("faixaSalarialMin", "2000")
                        .param("faixaSalarialMax", "1000"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/vagas").param("funcaoIds", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vagasCanceladasDoArtistaDevemTerCursorProprioEIsolamento() throws Exception {
        Usuario contratanteUsuario = criarUsuario("canceladas-paginadas-c@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(contratanteUsuario);
        Usuario artistaAUsuario = criarUsuario("canceladas-paginadas-a@teste.com", TipoUsuario.ARTISTA);
        PerfilArtista artistaA = criarArtista(artistaAUsuario);
        Usuario artistaBUsuario = criarUsuario("canceladas-paginadas-b@teste.com", TipoUsuario.ARTISTA);
        PerfilArtista artistaB = criarArtista(artistaBUsuario);

        for (int i = 1; i <= 3; i++) {
            var vaga = criarVaga(contratante, "Cancelada A " + i, "SP", ModeloTrabalho.REMOTO,
                    new BigDecimal("1000"), StatusVaga.CANCELADA);
            criarCandidatura(vaga, artistaA);
        }
        var vagaDoOutro = criarVaga(contratante, "Cancelada B", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.CANCELADA);
        criarCandidatura(vagaDoOutro, artistaB);

        MvcResult primeira = mockMvc.perform(get("/api/vagas")
                        .header("Authorization", "Bearer " + tokenPara(artistaAUsuario))
                        .param("size", "2")
                        .param("artistaId", artistaBUsuario.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vagasCanceladasComCandidatura.length()").value(2))
                .andExpect(jsonPath("$.hasMoreCanceladas").value(true))
                .andReturn();
        JsonNode jsonPrimeira = objectMapper.readTree(primeira.getResponse().getContentAsString());

        MvcResult segunda = mockMvc.perform(get("/api/vagas")
                        .header("Authorization", "Bearer " + tokenPara(artistaAUsuario))
                        .param("size", "2")
                        .param("cursorCanceladas", jsonPrimeira.get("nextCursorCanceladas").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vagasCanceladasComCandidatura.length()").value(1))
                .andExpect(jsonPath("$.hasMoreCanceladas").value(false))
                .andReturn();
        JsonNode jsonSegunda = objectMapper.readTree(segunda.getResponse().getContentAsString());

        assertThat(jsonSegunda.get("nextCursorCanceladas").isNull()).isTrue();
        assertThat(jsonPrimeira.toString()).doesNotContain("Cancelada B");
        assertThat(jsonSegunda.toString()).doesNotContain("Cancelada B");
    }

    @Test
    void similaresDevemUsarFuncoesSomenteAbertasEExcluirOrigem() throws Exception {
        Usuario usuario = criarUsuario("similares@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(usuario);
        Long funcaoComum = jdbcTemplate.queryForObject(
                "insert into funcoes(area_id, nome) values (1, ?) returning id", Long.class, "Música");
        Long funcaoDiferente = jdbcTemplate.queryForObject(
                "insert into funcoes(area_id, nome) values (1, ?) returning id", Long.class, "Fotografia similar");

        var origem = criarVaga(contratante, "Origem", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.ABERTA);
        var similar = criarVaga(contratante, "Similar aberta", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.ABERTA);
        var pausada = criarVaga(contratante, "Similar pausada", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.PAUSADA);
        var semCorrespondencia = criarVaga(contratante, "Sem correspondência", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.ABERTA);
        jdbcTemplate.update("insert into vaga_funcao (vaga_id, funcao_id) values (?, ?)", origem.getId(), funcaoComum);
        jdbcTemplate.update("insert into vaga_funcao (vaga_id, funcao_id) values (?, ?)", similar.getId(), funcaoComum);
        jdbcTemplate.update("insert into vaga_funcao (vaga_id, funcao_id) values (?, ?)", pausada.getId(), funcaoComum);
        jdbcTemplate.update("insert into vaga_funcao (vaga_id, funcao_id) values (?, ?)", semCorrespondencia.getId(), funcaoDiferente);

        mockMvc.perform(get("/api/vagas/{id}/similares", origem.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(similar.getId()))
                .andExpect(jsonPath("$.content[0].status").value("ABERTA"));
    }

    @Test
    void similaresSemFuncoesDevemRetornarPaginaVaziaEEndpointDeveSerPublico() throws Exception {
        Usuario usuario = criarUsuario("similares-sem-funcao@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(usuario);
        var origem = criarVaga(contratante, "Origem sem funcao", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.ABERTA);

        mockMvc.perform(get("/api/vagas/{id}/similares", origem.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void similaresDevemSerPaginadosSemDuplicacao() throws Exception {
        Usuario usuario = criarUsuario("similares-paginados@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(usuario);
        Long funcao = jdbcTemplate.queryForObject(
                "insert into funcoes(area_id, nome) values (1, ?) returning id", Long.class, "Teatro");
        var origem = criarVaga(contratante, "Origem teatro", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.ABERTA);
        jdbcTemplate.update("insert into vaga_funcao (vaga_id, funcao_id) values (?, ?)", origem.getId(), funcao);
        for (int i = 1; i <= 3; i++) {
            var similar = criarVaga(contratante, "Teatro " + i, "SP", ModeloTrabalho.REMOTO,
                    new BigDecimal("1000"), StatusVaga.ABERTA);
            jdbcTemplate.update("insert into vaga_funcao (vaga_id, funcao_id) values (?, ?)", similar.getId(), funcao);
        }

        MvcResult primeira = mockMvc.perform(get("/api/vagas/{id}/similares", origem.getId())
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn();
        JsonNode jsonPrimeira = objectMapper.readTree(primeira.getResponse().getContentAsString());

        MvcResult segunda = mockMvc.perform(get("/api/vagas/{id}/similares", origem.getId())
                        .param("size", "2")
                        .param("cursor", jsonPrimeira.get("nextCursor").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn();
        JsonNode jsonSegunda = objectMapper.readTree(segunda.getResponse().getContentAsString());
        assertThat(java.util.Collections.disjoint(
                extrairTitulos(jsonPrimeira), extrairTitulos(jsonSegunda))).isTrue();
    }

    @Test
    void deveCalcularPropriedadePeloJwtESemExporDadosPrivados() throws Exception {
        Usuario dono = criarUsuario("privacidade-dono@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante perfilDono = criarContratante(dono);
        var vagaDono = criarVaga(perfilDono, "Vaga própria", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.ABERTA);

        Usuario outro = criarUsuario("privacidade-outro@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante perfilOutro = criarContratante(outro);
        var vagaOutro = criarVaga(perfilOutro, "Vaga alheia", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.ABERTA);

        mockMvc.perform(get("/api/vagas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].propriaDoContratante").value(false))
                .andExpect(jsonPath("$.content[1].propriaDoContratante").value(false))
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].telefone").doesNotExist())
                .andExpect(jsonPath("$.content[0].senha").doesNotExist())
                .andExpect(jsonPath("$.content[0].tokenRecuperacao").doesNotExist())
                .andExpect(jsonPath("$.content[0].dataNascimento").doesNotExist());

        mockMvc.perform(get("/api/vagas")
                        .header("Authorization", "Bearer " + tokenPara(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(vagaDono.getId()))
                .andExpect(jsonPath("$.content[0].propriaDoContratante").value(true))
                .andExpect(jsonPath("$.content[1].id").value(vagaOutro.getId()))
                .andExpect(jsonPath("$.content[1].propriaDoContratante").value(false));
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
                  "areaId": 1, "abrangencia": "LOCAL", "valorMinimo": 750.00, "valorMaximo": 750.00,
                  "formaRemuneracao": "POR_EVENTO",
                  "cidade": "Sao Paulo",
                  "estado": "SP",
                  "modeloTrabalho": "PRESENCIAL",
                  "tipoContrato": "Freelance",
                  "categoria": "Fotografia",
                  "experiencia": "Intermediaria",
                  "dataLimiteCandidatura": "2030-05-05",
                  "abrangencia": "REGIONAL",
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
                .andExpect(jsonPath("$.categoria").value("Música"));

        mockMvc.perform(delete("/api/vagas/{id}", vagaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmacao":true,"motivo":"Projeto encerrado pelo contratante."}
                                """))
                .andExpect(status().isNoContent());

        assertThat(vagaRepository.findById(vagaId).orElseThrow().getStatus()).isEqualTo(StatusVaga.CANCELADA);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from log_vagas_canceladas where vaga_id = ?", Integer.class, vagaId)).isEqualTo(1);
    }

    @Test
    void perfilArtistaNovoDeveAplicarDefaultsDoSchema() {
        Usuario usuario = criarUsuario("defaults-artista@teste.com", TipoUsuario.ARTISTA);

        PerfilArtista perfil = criarArtista(usuario);

        assertThat(perfil.getTipoPerfilArtistico()).isEqualTo(com.portifolio.model.enums.TipoPerfilArtistico.ARTISTA_SOLO);
        assertThat(perfil.getRaioAtuacao()).isEqualTo(com.portifolio.model.enums.Abrangencia.LOCAL);
        assertThat(perfil.getUltimaAtualizacao()).isNotNull();
    }

    @Test
    void artistaComPerfilCompletoDeveCriarCandidaturaSemprePendente() throws Exception {
        Usuario contratanteUsuario = criarUsuario("candidatura-contratante@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(contratanteUsuario);
        var vaga = criarVaga(contratante, "Vaga aberta", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.ABERTA);

        Usuario artistaUsuario = criarUsuario("candidatura-artista@teste.com", TipoUsuario.ARTISTA);
        artistaUsuario.setPerfilCompleto(true);
        usuarioRepository.save(artistaUsuario);
        criarArtista(artistaUsuario);

        String corpo = """
                {
                  "vagaId": %d,
                  "artistaId": %d,
                  "mensagemApresentacao": "Tenho interesse nesta oportunidade.",
                  "linkPortfolioCandidatura": "https://exemplo.com/portfolio",
                  "status": "ACEITA"
                }
                """.formatted(vaga.getId(), artistaUsuario.getId());

        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", "Bearer " + tokenPara(artistaUsuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.artistaId").value(artistaUsuario.getId()));
    }

    @Test
    void candidaturaDeveRecusarPerfilIncompletoEVagaCancelada() throws Exception {
        Usuario contratanteUsuario = criarUsuario("candidatura-regras-c@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(contratanteUsuario);
        var vagaAberta = criarVaga(contratante, "Vaga aberta", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.ABERTA);
        var vagaCancelada = criarVaga(contratante, "Vaga cancelada", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.CANCELADA);

        Usuario artistaUsuario = criarUsuario("candidatura-regras-a@teste.com", TipoUsuario.ARTISTA);
        criarArtista(artistaUsuario);
        String token = tokenPara(artistaUsuario);

        String perfilIncompleto = corpoCandidatura(vagaAberta.getId(), artistaUsuario.getId());
        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(perfilIncompleto))
                .andExpect(status().isUnprocessableEntity());

        artistaUsuario.setPerfilCompleto(true);
        usuarioRepository.save(artistaUsuario);
        String vagaSemCandidatura = corpoCandidatura(vagaCancelada.getId(), artistaUsuario.getId());
        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", "Bearer " + tokenPara(artistaUsuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vagaSemCandidatura))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void camposDeIdentidadeNoPayloadNaoSubstituemArtistaDoJwt() throws Exception {
        Usuario contratanteUsuario = criarUsuario("candidatura-identidade-c@teste.com", TipoUsuario.CONTRATANTE);
        PerfilContratante contratante = criarContratante(contratanteUsuario);
        var vaga = criarVaga(contratante, "Vaga aberta", "SP", ModeloTrabalho.REMOTO,
                new BigDecimal("1000"), StatusVaga.ABERTA);

        Usuario autenticado = criarUsuario("artista-autenticado@teste.com", TipoUsuario.ARTISTA);
        autenticado.setPerfilCompleto(true);
        usuarioRepository.save(autenticado);
        criarArtista(autenticado);

        Usuario outro = criarUsuario("outro-artista@teste.com", TipoUsuario.ARTISTA);
        outro.setPerfilCompleto(true);
        usuarioRepository.save(outro);
        criarArtista(outro);

        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", "Bearer " + tokenPara(autenticado))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCandidatura(vaga.getId(), outro.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.artistaId").value(autenticado.getId()))
                .andExpect(jsonPath("$.status").value("PENDENTE"));

        assertThat(candidaturaRepository.findAll().getFirst().getArtista().getUsuarioId())
                .isEqualTo(autenticado.getId());
    }

    @Test
    void atualizacaoDoProprioPerfilArtistaDeveMarcaLoComoCompleto() throws Exception {
        Usuario artista = criarUsuario("perfil-proprio@teste.com", TipoUsuario.ARTISTA);
        criarArtista(artista);
        Usuario outroArtista = criarUsuario("perfil-outro@teste.com", TipoUsuario.ARTISTA);
        criarArtista(outroArtista);
        Long funcaoId = jdbcTemplate.queryForObject(
                "insert into funcoes(area_id, nome) values (1, ?) returning id", Long.class, "Funcao perfil completo");

        String corpo = """
                {
                  "usuarioId": %d,
                  "biografia": "Biografia completa",
                  "localizacao": "São Paulo, SP",
                  "urlPortfolio": "https://portfolio.example",
                  "areaPrincipalId": 1, "tipoPerfilArtistico": "ARTISTA_SOLO", "raioAtuacao": "LOCAL", "funcaoIds": [%d]
                }
                """.formatted(artista.getId(), funcaoId);

        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getId())
                        .header("Authorization", "Bearer " + tokenPara(outroArtista))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/perfis-artistas/{id}", artista.getId())
                        .header("Authorization", "Bearer " + tokenPara(artista))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());

        assertThat(usuarioRepository.findById(artista.getId()).orElseThrow().getPerfilCompleto()).isTrue();
    }

    private String corpoCandidatura(Long vagaId, Long artistaId) {
        return """
                {
                  "vagaId": %d,
                  "artistaId": %d,
                  "mensagemApresentacao": "Tenho interesse nesta oportunidade.",
                  "linkPortfolioCandidatura": "https://exemplo.com/portfolio"
                }
                """.formatted(vagaId, artistaId);
    }
}
