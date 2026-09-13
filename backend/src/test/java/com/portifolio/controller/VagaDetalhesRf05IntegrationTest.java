package com.portifolio.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portifolio.model.Candidatura;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Funcao;
import com.portifolio.model.Usuario;
import com.portifolio.model.Vaga;
import com.portifolio.model.enums.ModeloTrabalho;
import com.portifolio.model.enums.StatusCandidatura;
import com.portifolio.model.enums.StatusVaga;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.CandidaturaRepository;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.FuncaoRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.repository.VagaRepository;
import com.portifolio.security.JwtService;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class VagaDetalhesRf05IntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScripts("db/schema-test.sql", "db/catalogo-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PerfilContratanteRepository perfilContratanteRepository;
    @Autowired PerfilArtistaRepository perfilArtistaRepository;
    @Autowired FuncaoRepository funcaoRepository;
    @Autowired VagaRepository vagaRepository;
    @Autowired CandidaturaRepository candidaturaRepository;
    @Autowired JwtService jwtService;
    @Autowired EntityManagerFactory entityManagerFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void limparBanco() {
        jdbcTemplate.execute("TRUNCATE candidaturas, vagas, funcoes, perfis_artistas, "
                + "perfis_contratantes, usuarios RESTART IDENTITY CASCADE");
    }

    @Test
    void detalheAbertoDeveRetornarCamposFuncoesEContratantePublicoSemDadosPrivados() throws Exception {
        PerfilContratante contratante = novoContratante("empresa-publica@rf05.test");
        Funcao musica = novaFuncao("Música");
        Funcao violao = novaFuncao("Violão");
        Vaga vaga = novaVaga(contratante, StatusVaga.ABERTA, musica, violao);
        Usuario artista = novoUsuario("artista-detalhe@rf05.test", TipoUsuario.ARTISTA, false);

        detalhar(vaga, artista)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vaga.getId()))
                .andExpect(jsonPath("$.titulo").value("Vaga RF05"))
                .andExpect(jsonPath("$.descricao").value("Descrição detalhada"))
                .andExpect(jsonPath("$.requisitos").value("Requisitos profissionais"))
                .andExpect(jsonPath("$.status").value("ABERTA"))
                .andExpect(jsonPath("$.funcaoIds.length()").value(2))
                .andExpect(jsonPath("$.contratantePublico.nomeEmpresa").value("Empresa RF05"))
                .andExpect(jsonPath("$.contratantePublico.biografia").value("Biografia profissional"))
                .andExpect(jsonPath("$.contratantePublico.localizacao").value("Campinas, SP"))
                .andExpect(jsonPath("$.contratantePublico.avatarUrl").isNotEmpty())
                .andExpect(jsonPath("$.propriaDoContratante").value(false))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefone").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.contratantePublico.email").doesNotExist())
                .andExpect(jsonPath("$.contratantePublico.telefone").doesNotExist())
                .andExpect(jsonPath("$.contratantePublico.dataNascimento").doesNotExist())
                .andExpect(jsonPath("$.contratantePublico.nomeResponsavel").doesNotExist());
    }

    @Test
    void detalheDeVagaInexistenteDeveRetornar404() throws Exception {
        Usuario contratante = novoUsuario("inexistente@rf05.test", TipoUsuario.CONTRATANTE, false);

        mockMvc.perform(get("/api/vagas/{id}", 999999)
                        .header("Authorization", bearer(contratante)))
                .andExpect(status().isNotFound());
    }

    @Test
    void propriedadeDeveSerCalculadaPeloJwtEVisitanteNaoRecebePrivilegio() throws Exception {
        PerfilContratante dono = novoContratante("dono-propriedade@rf05.test");
        PerfilContratante outro = novoContratante("outro-propriedade@rf05.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);

        detalhar(vaga, dono.getUsuario())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propriaDoContratante").value(true));
        detalhar(vaga, outro.getUsuario())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propriaDoContratante").value(false));
    }

    @Test
    void anonimoDeveAcessarVagaAbertaSemContextoDaSessao() throws Exception {
        PerfilContratante dono = novoContratante("anonimo-aberta@rf05.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);

        mockMvc.perform(get("/api/vagas/{id}", vaga.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABERTA"))
                .andExpect(jsonPath("$.propriaDoContratante").value(false))
                .andExpect(jsonPath("$.minhaCandidaturaId").value(nullValue()))
                .andExpect(jsonPath("$.statusMinhaCandidatura").value(nullValue()));
    }

    @ParameterizedTest
    @EnumSource(value = StatusVaga.class, names = {"PAUSADA", "ENCERRADA", "CANCELADA"})
    void anonimoNaoDeveDescobrirVagaForaDoFeed(StatusVaga statusVaga) throws Exception {
        PerfilContratante dono = novoContratante("anonimo-oculta-" + statusVaga + "@rf05.test");
        Vaga vaga = novaVaga(dono, statusVaga);

        mockMvc.perform(get("/api/vagas/{id}", vaga.getId()))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(value = StatusVaga.class, names = {"PAUSADA", "ENCERRADA", "CANCELADA"})
    void proprietarioDeveAcessarVagaForaDoFeed(StatusVaga statusVaga) throws Exception {
        PerfilContratante dono = novoContratante("dono-" + statusVaga + "@rf05.test");
        Vaga vaga = novaVaga(dono, statusVaga);

        detalhar(vaga, dono.getUsuario())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(statusVaga.name()))
                .andExpect(jsonPath("$.propriaDoContratante").value(true));
    }

    @ParameterizedTest
    @EnumSource(value = StatusVaga.class, names = {"PAUSADA", "ENCERRADA", "CANCELADA"})
    void artistaSemCandidaturaNaoDeveDescobrirVagaForaDoFeed(StatusVaga statusVaga) throws Exception {
        PerfilContratante dono = novoContratante("oculta-" + statusVaga + "@rf05.test");
        Vaga vaga = novaVaga(dono, statusVaga);
        Usuario artista = novoUsuario("sem-candidatura-" + statusVaga + "@rf05.test",
                TipoUsuario.ARTISTA, true);

        detalhar(vaga, artista)
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(value = StatusVaga.class, names = {"PAUSADA", "ENCERRADA", "CANCELADA"})
    void artistaCandidatoDeveAcessarVagaForaDoFeedComContextoDaPropriaCandidatura(
            StatusVaga statusVaga) throws Exception {
        PerfilContratante dono = novoContratante("historico-" + statusVaga + "@rf05.test");
        Vaga vaga = novaVaga(dono, statusVaga);
        PerfilArtista artista = novoArtista("candidato-historico-" + statusVaga + "@rf05.test",
                LocalDateTime.of(2026, 8, 1, 10, 0));
        Candidatura candidatura = novaCandidatura(vaga, artista, 0);

        detalhar(vaga, artista.getUsuario())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minhaCandidaturaId").value(candidatura.getId()))
                .andExpect(jsonPath("$.statusMinhaCandidatura").value("PENDENTE"))
                .andExpect(jsonPath("$.propriaDoContratante").value(false));
    }

    @Test
    void liberacaoPublicaNaoDeveExporRotasNomeadasDeVagas() throws Exception {
        mockMvc.perform(get("/api/vagas/minhas"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/vagas/nao-numerico"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void candidaturasSemJwtDevemRetornar401() throws Exception {
        PerfilContratante dono = novoContratante("candidaturas-401@rf05.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);

        mockMvc.perform(get("/api/vagas/{id}/candidaturas", vaga.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void artistaNaoPodeListarCandidaturasDaVaga() throws Exception {
        PerfilContratante dono = novoContratante("candidaturas-artista@rf05.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        Usuario artista = novoUsuario("artista-idor@rf05.test", TipoUsuario.ARTISTA, true);

        listarCandidaturas(vaga, artista, null, null)
                .andExpect(status().isForbidden());
    }

    @Test
    void outroContratanteNaoPodeListarCandidaturasDaVaga() throws Exception {
        PerfilContratante dono = novoContratante("dono-idor@rf05.test");
        PerfilContratante intruso = novoContratante("intruso-idor@rf05.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);

        listarCandidaturas(vaga, intruso.getUsuario(), null, null)
                .andExpect(status().isForbidden());
    }

    @Test
    void candidaturasDeVagaInexistenteDevemRetornar404() throws Exception {
        PerfilContratante contratante = novoContratante("candidaturas-404@rf05.test");

        mockMvc.perform(get("/api/vagas/{id}/candidaturas", 999999)
                        .header("Authorization", bearer(contratante.getUsuario())))
                .andExpect(status().isNotFound());
    }

    @Test
    void proprietarioDeveReceberDadosProfissionaisSemDadosPrivadosNemMedalhas() throws Exception {
        PerfilContratante dono = novoContratante("dono-privacidade@rf05.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        Usuario menor = novoUsuario("menor-privado@rf05.test", TipoUsuario.ARTISTA, true);
        menor.setDataNascimento(LocalDate.of(2012, 5, 10));
        menor.setNomeResponsavel("Responsável Secreto");
        menor.setTelefoneResponsavel("11911112222");
        menor.setEmailResponsavel("responsavel@privado.test");
        usuarioRepository.save(menor);
        PerfilArtista perfil = novoPerfilArtista(
                menor, LocalDateTime.of(2026, 8, 10, 12, 0));
        novaCandidatura(vaga, perfil, 0);

        listarCandidaturas(vaga, dono.getUsuario(), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nomeArtista").value("Artista RF05"))
                .andExpect(jsonPath("$.content[0].biografia").value("Biografia pública"))
                .andExpect(jsonPath("$.content[0].localizacao").value("São Paulo, SP"))
                .andExpect(jsonPath("$.content[0].urlPortfolio").value("https://portfolio.example"))
                .andExpect(jsonPath("$.content[0].mensagemApresentacao").value("Mensagem profissional"))
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].telefone").doesNotExist())
                .andExpect(jsonPath("$.content[0].dataNascimento").doesNotExist())
                .andExpect(jsonPath("$.content[0].nomeResponsavel").doesNotExist())
                .andExpect(jsonPath("$.content[0].telefoneResponsavel").doesNotExist())
                .andExpect(jsonPath("$.content[0].emailResponsavel").doesNotExist())
                .andExpect(jsonPath("$.content[0].senha").doesNotExist())
                .andExpect(jsonPath("$.content[0].token").doesNotExist())
                .andExpect(jsonPath("$.content[0].nivelMedalha").doesNotExist())
                .andExpect(jsonPath("$.content[0].scoreEngajamento").doesNotExist());
    }

    @Test
    void candidatosDevemSerOrdenadosPorFuncoesAtualizacaoEId() throws Exception {
        PerfilContratante dono = novoContratante("ordenacao@rf05.test");
        Funcao musica = novaFuncao("Música");
        Funcao violao = novaFuncao("Violão");
        Funcao teatro = novaFuncao("Teatro");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA, musica, violao);

        PerfilArtista umaFuncao = novoArtista("uma-funcao@rf05.test",
                LocalDateTime.of(2026, 8, 20, 10, 0), musica);
        PerfilArtista duasRecentes = novoArtista("duas-recente@rf05.test",
                LocalDateTime.of(2026, 8, 20, 12, 0), musica, violao);
        PerfilArtista duasEmpate1 = novoArtista("duas-empate1@rf05.test",
                LocalDateTime.of(2026, 8, 19, 12, 0), musica, violao);
        PerfilArtista duasEmpate2 = novoArtista("duas-empate2@rf05.test",
                LocalDateTime.of(2026, 8, 19, 12, 0), musica, violao);
        PerfilArtista semCoincidencia = novoArtista("zero-funcao@rf05.test",
                LocalDateTime.of(2026, 8, 21, 12, 0), teatro);

        Candidatura cUma = novaCandidatura(vaga, umaFuncao, 1);
        Candidatura cRecente = novaCandidatura(vaga, duasRecentes, 2);
        Candidatura cEmpate1 = novaCandidatura(vaga, duasEmpate1, 3);
        Candidatura cEmpate2 = novaCandidatura(vaga, duasEmpate2, 4);
        Candidatura cZero = novaCandidatura(vaga, semCoincidencia, 5);

        listarCandidaturas(vaga, dono.getUsuario(), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].candidaturaId").value(cRecente.getId()))
                .andExpect(jsonPath("$.content[1].candidaturaId").value(cEmpate1.getId()))
                .andExpect(jsonPath("$.content[2].candidaturaId").value(cEmpate2.getId()))
                .andExpect(jsonPath("$.content[3].candidaturaId").value(cUma.getId()))
                .andExpect(jsonPath("$.content[4].candidaturaId").value(cZero.getId()))
                .andExpect(jsonPath("$.content[0].quantidadeFuncoesCoincidentes").value(2))
                .andExpect(jsonPath("$.content[0].funcoesCoincidentes.length()").value(2))
                .andExpect(jsonPath("$.content[3].quantidadeFuncoesCoincidentes").value(1))
                .andExpect(jsonPath("$.content[4].quantidadeFuncoesCoincidentes").value(0));
    }

    @Test
    void vagaSemFuncoesDeveProduzirCompatibilidadeZeroEOrdemDeterministica() throws Exception {
        PerfilContratante dono = novoContratante("vaga-sem-funcoes@rf05.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        Funcao funcao = novaFuncao("Dança");
        PerfilArtista antigo = novoArtista("antigo-sem-vaga-funcoes@rf05.test",
                LocalDateTime.of(2026, 8, 1, 10, 0), funcao);
        PerfilArtista recente = novoArtista("recente-sem-vaga-funcoes@rf05.test",
                LocalDateTime.of(2026, 8, 2, 10, 0));
        Candidatura cAntiga = novaCandidatura(vaga, antigo, 0);
        Candidatura cRecente = novaCandidatura(vaga, recente, 1);

        listarCandidaturas(vaga, dono.getUsuario(), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].candidaturaId").value(cRecente.getId()))
                .andExpect(jsonPath("$.content[1].candidaturaId").value(cAntiga.getId()))
                .andExpect(jsonPath("$.content[0].quantidadeFuncoesCoincidentes").value(0))
                .andExpect(jsonPath("$.content[1].quantidadeFuncoesCoincidentes").value(0));
    }

    @Test
    void paginacaoDeveAplicarPadraoMaximoUltimaPaginaESemDuplicacao() throws Exception {
        PerfilContratante dono = novoContratante("paginacao@rf05.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        for (int i = 0; i < 52; i++) {
            PerfilArtista artista = novoArtista("paginado-" + i + "@rf05.test",
                    LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(i));
            novaCandidatura(vaga, artista, i);
        }

        MvcResult padrao = listarCandidaturas(vaga, dono.getUsuario(), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(52))
                .andReturn();
        MvcResult primeira = listarCandidaturas(vaga, dono.getUsuario(), 0, 500)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(50))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn();
        MvcResult ultima = listarCandidaturas(vaga, dono.getUsuario(), 1, 50)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.last").value(true))
                .andReturn();

        assertThat(ids(primeira)).doesNotContainAnyElementsOf(ids(ultima));
        assertThat(ids(padrao)).hasSize(20);
    }

    @Test
    void parametrosDePaginacaoInvalidosDevemRetornar400() throws Exception {
        PerfilContratante dono = novoContratante("pagina-invalida@rf05.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);

        listarCandidaturas(vaga, dono.getUsuario(), -1, 20)
                .andExpect(status().isBadRequest());
        listarCandidaturas(vaga, dono.getUsuario(), 0, 0)
                .andExpect(status().isBadRequest());
    }

    @Test
    void listagemLegadaDeveSerLimitadaEPaginavelSemQuebrarContratoDeArray() throws Exception {
        PerfilContratante dono = novoContratante("legado-paginado@rf05.test");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA);
        for (int i = 0; i < 25; i++) {
            novaCandidatura(vaga, novoArtista("legado-" + i + "@rf05.test",
                    LocalDateTime.of(2026, 2, 1, 0, 0).plusMinutes(i)), i);
        }

        mockMvc.perform(get("/api/candidaturas/minhas-vagas")
                        .header("Authorization", bearer(dono.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20));
        mockMvc.perform(get("/api/candidaturas/minhas-vagas")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", bearer(dono.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void carregamentoDeVinteCandidatosNaoDeveExecutarQueryPorCandidato() throws Exception {
        PerfilContratante dono = novoContratante("nmaisum@rf05.test");
        Funcao funcao = novaFuncao("Performance");
        Vaga vaga = novaVaga(dono, StatusVaga.ABERTA, funcao);
        for (int i = 0; i < 20; i++) {
            novaCandidatura(vaga, novoArtista("nmaisum-" + i + "@rf05.test",
                    LocalDateTime.of(2026, 3, 1, 0, 0).plusMinutes(i), funcao), i);
        }
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().clear();

        listarCandidaturas(vaga, dono.getUsuario(), 0, 20)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20));

        assertThat(sessionFactory.getStatistics().getPrepareStatementCount())
                .as("consultas devem permanecer constantes, não crescer por candidato")
                .isLessThanOrEqualTo(8);
    }

    private org.springframework.test.web.servlet.ResultActions detalhar(
            Vaga vaga, Usuario usuario) throws Exception {
        return mockMvc.perform(get("/api/vagas/{id}", vaga.getId())
                .header("Authorization", bearer(usuario)));
    }

    private org.springframework.test.web.servlet.ResultActions listarCandidaturas(
            Vaga vaga, Usuario usuario, Integer page, Integer size) throws Exception {
        var request = get("/api/vagas/{id}/candidaturas", vaga.getId())
                .header("Authorization", bearer(usuario));
        if (page != null) {
            request.param("page", page.toString());
        }
        if (size != null) {
            request.param("size", size.toString());
        }
        return mockMvc.perform(request);
    }

    private Set<Long> ids(MvcResult resultado) throws Exception {
        JsonNode content = objectMapper.readTree(
                resultado.getResponse().getContentAsString()).get("content");
        Set<Long> ids = new HashSet<>();
        content.forEach(item -> ids.add(item.get("candidaturaId").asLong()));
        return ids;
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.gerarToken(usuario);
    }

    private PerfilContratante novoContratante(String email) {
        Usuario usuario = novoUsuario(email, TipoUsuario.CONTRATANTE, false);
        usuario.setNome("Nome Público Contratante");
        usuario.setNomeResponsavel("Responsável Privado");
        usuario.setTelefoneResponsavel("11988887777");
        usuario.setEmailResponsavel("responsavel@privado.test");
        usuarioRepository.save(usuario);
        PerfilContratante perfil = new PerfilContratante();
        perfil.setUsuario(usuario);
        perfil.setNomeEmpresa("Empresa RF05");
        perfil.setTipoPerfil("Produtora");
        perfil.setBiografia("Biografia profissional");
        perfil.setLocalizacao("Campinas, SP");
        perfil.setBannerUrl("https://example.com/banner.jpg");
        return perfilContratanteRepository.save(perfil);
    }

    private PerfilArtista novoArtista(
            String email, LocalDateTime ultimaAtualizacao, Funcao... funcoes) {
        Usuario usuario = novoUsuario(email, TipoUsuario.ARTISTA, true);
        return novoPerfilArtista(usuario, ultimaAtualizacao, funcoes);
    }

    private PerfilArtista novoPerfilArtista(
            Usuario usuario, LocalDateTime ultimaAtualizacao, Funcao... funcoes) {
        PerfilArtista perfil = new PerfilArtista();
        perfil.setTipoPerfilArtistico(com.portifolio.model.enums.TipoPerfilArtistico.ARTISTA_SOLO);
        perfil.setRaioAtuacao(com.portifolio.model.enums.Abrangencia.LOCAL);
        perfil.setUsuario(usuario);
        perfil.setBiografia("Biografia pública");
        perfil.setLocalizacao("São Paulo, SP");
        perfil.setUrlPortfolio("https://portfolio.example");
        perfil.setUltimaAtualizacao(ultimaAtualizacao);
        com.portifolio.support.OfficialSchemaFixtures.funcoes(perfil, new HashSet<>(Set.of(funcoes)));
        return perfilArtistaRepository.save(perfil);
    }

    private Usuario novoUsuario(String email, TipoUsuario tipo, boolean completo) {
        Usuario usuario = new Usuario();
        usuario.setNome(tipo == TipoUsuario.ARTISTA ? "Artista RF05" : "Contratante RF05");
        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setTelefone("11999999999");
        usuario.setEmail(email);
        usuario.setSenha("hash-privado");
        usuario.setTipoUsuario(tipo);
        usuario.setPerfilCompleto(completo);
        usuario.setDataCriacao(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    private Funcao novaFuncao(String nome) {
        Funcao funcao = new Funcao();
        funcao.setArea(com.portifolio.support.OfficialSchemaFixtures.area());
        funcao.setNome(nome);
        return funcaoRepository.save(funcao);
    }

    private Vaga novaVaga(
            PerfilContratante contratante, StatusVaga status, Funcao... funcoes) {
        Vaga vaga = new Vaga();
        vaga.setArea(com.portifolio.support.OfficialSchemaFixtures.area());
        vaga.setAbrangencia(com.portifolio.model.enums.Abrangencia.LOCAL);
        vaga.setContratante(contratante);
        vaga.setTitulo("Vaga RF05");
        vaga.setDescricao("Descrição detalhada");
        vaga.setRequisitos("Requisitos profissionais");
        vaga.setValorMinimo(new BigDecimal("2500.00"));
        vaga.setValorMaximo(new BigDecimal("2500.00"));
        vaga.setFormaRemuneracao(com.portifolio.model.enums.FormaRemuneracao.POR_EVENTO);
        vaga.setCidade("Campinas");
        vaga.setEstado("SP");
        vaga.setEnderecoCompleto("Rua da Oportunidade, 10");
        vaga.setBeneficios("Transporte");
        vaga.setModeloTrabalho(ModeloTrabalho.HIBRIDO);
        vaga.setTipoContrato("Freelance");
        vaga.setArea(com.portifolio.support.OfficialSchemaFixtures.area((short) 1));
        vaga.setStatus(status);
        vaga.setDataPublicacao(LocalDateTime.of(2026, 8, 1, 9, 0));
        vaga.setFuncoes(new HashSet<>(Set.of(funcoes)));
        return vagaRepository.save(vaga);
    }

    private Candidatura novaCandidatura(
            Vaga vaga, PerfilArtista artista, int ordem) {
        Candidatura candidatura = new Candidatura();
        candidatura.setVaga(vaga);
        candidatura.setArtista(artista);
        candidatura.setMensagemApresentacao("Mensagem profissional");
        candidatura.setLinkPortfolioCandidatura("https://portfolio.example/candidatura");
        candidatura.setStatus(StatusCandidatura.PENDENTE);
        candidatura.setDataCandidatura(
                LocalDateTime.of(2026, 8, 1, 10, 0).plusMinutes(ordem));
        return candidaturaRepository.save(candidatura);
    }
}
