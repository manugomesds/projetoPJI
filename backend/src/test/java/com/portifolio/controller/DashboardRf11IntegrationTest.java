package com.portifolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class DashboardRf11IntegrationTest {

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
    @Autowired VagaRepository vagaRepository;
    @Autowired CandidaturaRepository candidaturaRepository;
    @Autowired JwtService jwtService;
    @Autowired EntityManagerFactory entityManagerFactory;

    @AfterEach
    void limparBanco() {
        jdbcTemplate.execute("""
                TRUNCATE candidaturas, vagas, funcoes, perfis_artistas,
                         perfis_contratantes, usuarios RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void endpointExigeJwtValido() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void artistaRecebeSomenteVagasAbertasComFuncoesCoincidentesOrdenadasPorCompatibilidade() throws Exception {
        PerfilArtista artista = criarArtista("artista@rf11.test", true, LocalDate.of(1990, 1, 1));
        Funcao teatro = criarFuncao("Teatro");
        Funcao danca = criarFuncao("Danca");
        Funcao musica = criarFuncao("Musica");
        com.portifolio.support.OfficialSchemaFixtures.funcoes(artista, new HashSet<>(Set.of(teatro, danca)));
        perfilArtistaRepository.save(artista);

        PerfilContratante contratante = criarContratante("contratante@rf11.test", true);
        criarVaga(contratante, "Uma coincidencia", StatusVaga.ABERTA, teatro);
        criarVaga(contratante, "Duas coincidencias", StatusVaga.ABERTA, teatro, danca);
        criarVaga(contratante, "Pausada", StatusVaga.PAUSADA, teatro, danca);
        criarVaga(contratante, "Encerrada", StatusVaga.ENCERRADA, teatro, danca);
        criarVaga(contratante, "Cancelada", StatusVaga.CANCELADA, teatro, danca);
        criarVaga(contratante, "Sem coincidencia", StatusVaga.ABERTA, musica);

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", bearer(artista.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoUsuario").value("ARTISTA"))
                .andExpect(jsonPath("$.vagasRecomendadas.content.length()").value(2))
                .andExpect(jsonPath("$.vagasRecomendadas.content[0].titulo").value("Duas coincidencias"))
                .andExpect(jsonPath("$.vagasRecomendadas.content[0].quantidadeFuncoesCoincidentes").value(2))
                .andExpect(jsonPath("$.vagasRecomendadas.content[1].titulo").value("Uma coincidencia"))
                .andExpect(jsonPath("$.candidaturasRecentes").doesNotExist())
                .andExpect(jsonPath("$.talentosSugeridos").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefone").doesNotExist())
                .andExpect(jsonPath("$.dataNascimento").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    void artistaSemFuncoesNaoRecebeSugestaoAleatoriaEEnxergaPerfilIncompleto() throws Exception {
        PerfilArtista artista = criarArtista("sem-funcoes@rf11.test", false, LocalDate.of(1992, 2, 2));
        PerfilContratante contratante = criarContratante("publicador@rf11.test", true);
        criarVaga(contratante, "Vaga qualquer", StatusVaga.ABERTA, criarFuncao("Cinema"));

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", bearer(artista.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfilCompleto").value(false))
                .andExpect(jsonPath("$.vagasRecomendadas.content.length()").value(0))
                .andExpect(jsonPath("$.vagasRecomendadas.totalElements").value(0))
                .andExpect(jsonPath("$.vagasRecomendadas.hasMore").value(false));
    }

    @Test
    void perfilIncompletoNaoBloqueiaDashboardNemVisualizacaoDeRecomendacoes() throws Exception {
        PerfilArtista artista = criarArtista("incompleto-com-funcao@rf11.test", false, LocalDate.of(1992, 2, 2));
        Funcao funcao = criarFuncao("Teatro incompleto");
        com.portifolio.support.OfficialSchemaFixtures.funcoes(artista, new HashSet<>(Set.of(funcao)));
        perfilArtistaRepository.save(artista);
        PerfilContratante contratante = criarContratante("publicador-incompleto@rf11.test", true);
        criarVaga(contratante, "Vaga visivel", StatusVaga.ABERTA, funcao);

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", bearer(artista.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfilCompleto").value(false))
                .andExpect(jsonPath("$.vagasRecomendadas.content[0].titulo").value("Vaga visivel"));
    }

    @Test
    void identidadeVemDoJwtEMesmoParametroDeOutroUsuarioNaoProduzIdor() throws Exception {
        PerfilArtista autenticado = criarArtista("autenticado@rf11.test", true, LocalDate.of(1990, 1, 1));
        PerfilArtista outro = criarArtista("outro@rf11.test", false, LocalDate.of(1990, 1, 1));

        mockMvc.perform(get("/api/dashboard")
                        .param("usuarioId", outro.getUsuarioId().toString())
                        .header("Authorization", bearer(autenticado.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao").value(autenticado.getUsuario().getNome()))
                .andExpect(jsonPath("$.perfilCompleto").value(true));
    }

    @Test
    void sizeEhLimitadoEntreUmECinquenta() throws Exception {
        PerfilArtista artista = criarArtista("size@rf11.test", true, LocalDate.of(1990, 1, 1));
        String token = bearer(artista.getUsuario());

        mockMvc.perform(get("/api/dashboard").param("size", "0").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("size deve estar entre 1 e 50."));
        mockMvc.perform(get("/api/dashboard").param("size", "51").header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void contratanteVeCandidaturasRecentesSomenteDasPropriasVagasAtivas() throws Exception {
        PerfilContratante dono = criarContratante("dono@rf11.test", true);
        PerfilContratante terceiro = criarContratante("terceiro@rf11.test", true);
        PerfilArtista artista = criarArtista("candidato@rf11.test", true, LocalDate.of(1990, 1, 1));
        Vaga aberta = criarVaga(dono, "Aberta propria", StatusVaga.ABERTA);
        Vaga pausada = criarVaga(dono, "Pausada propria", StatusVaga.PAUSADA);
        Vaga encerrada = criarVaga(dono, "Encerrada propria", StatusVaga.ENCERRADA);
        Vaga alheia = criarVaga(terceiro, "Aberta alheia", StatusVaga.ABERTA);
        criarCandidatura(aberta, artista, LocalDateTime.now().minusHours(2));
        criarCandidatura(pausada, artista, LocalDateTime.now().minusHours(1));
        criarCandidatura(encerrada, artista, LocalDateTime.now());
        criarCandidatura(alheia, artista, LocalDateTime.now().plusHours(1));

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", bearer(dono.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoUsuario").value("CONTRATANTE"))
                .andExpect(jsonPath("$.candidaturasRecentes.content.length()").value(2))
                .andExpect(jsonPath("$.candidaturasRecentes.content[0].tituloVaga").value("Pausada propria"))
                .andExpect(jsonPath("$.candidaturasRecentes.content[1].tituloVaga").value("Aberta propria"))
                .andExpect(jsonPath("$.vagasRecomendadas").doesNotExist())
                .andExpect(jsonPath("$.candidaturasRecentes.content[0].mensagemApresentacao").doesNotExist())
                .andExpect(jsonPath("$.candidaturasRecentes.content[0].linkPortfolioCandidatura").doesNotExist());
    }

    @Test
    void talentosUsamFuncoesDasVagasAtivasEExcluemPerfilIncompletoEMenor() throws Exception {
        PerfilContratante dono = criarContratante("matching@rf11.test", true);
        Funcao teatro = criarFuncao("Teatro RF11");
        Funcao cinema = criarFuncao("Cinema RF11");
        Funcao circo = criarFuncao("Circo RF11");
        criarVaga(dono, "Vaga ativa", StatusVaga.PAUSADA, teatro, cinema);
        criarVaga(dono, "Vaga cancelada", StatusVaga.CANCELADA, circo);
        PerfilArtista compativel = criarArtista("compativel@rf11.test", true, LocalDate.of(1990, 1, 1), teatro, cinema);
        criarArtista("incompleto@rf11.test", false, LocalDate.of(1990, 1, 1), teatro, cinema);
        criarArtista("menor@rf11.test", true, LocalDate.now().minusYears(17), teatro, cinema);
        criarArtista("funcao-cancelada@rf11.test", true, LocalDate.of(1990, 1, 1), circo);

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", bearer(dono.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.talentosSugeridos.content.length()").value(2))
                .andExpect(jsonPath("$.talentosSugeridos.content[0].artistaId").value(compativel.getUsuarioId()))
                .andExpect(jsonPath("$.talentosSugeridos.content[0].quantidadeFuncoesCoincidentes").value(2))
                .andExpect(jsonPath("$.talentosSugeridos.content[1].quantidadeFuncoesCoincidentes").value(0))
                .andExpect(jsonPath("$.talentosSugeridos.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.talentosSugeridos.content[0].dataNascimento").doesNotExist());
    }

    @Test
    void contratanteSemVagaAtivaComFuncoesNaoRecebeTalentoAleatorio() throws Exception {
        PerfilContratante dono = criarContratante("sem-contexto@rf11.test", true);
        Funcao funcao = criarFuncao("Sem contexto");
        criarVaga(dono, "Encerrada", StatusVaga.ENCERRADA, funcao);
        criarArtista("artista-aleatorio@rf11.test", true, LocalDate.of(1990, 1, 1), funcao);

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", bearer(dono.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.talentosSugeridos.content.length()").value(0))
                .andExpect(jsonPath("$.talentosSugeridos.totalElements").value(0));
    }

    @Test
    void talentosOrdenamPorCoincidenciaDepoisPorAtualizacaoMaisRecente() throws Exception {
        PerfilContratante dono = criarContratante("ordem-talentos@rf11.test", true);
        Funcao teatro = criarFuncao("Teatro ordem");
        Funcao musica = criarFuncao("Musica ordem");
        criarVaga(dono, "Contexto", StatusVaga.ABERTA, teatro, musica);
        PerfilArtista duasFuncoesAntigo = criarArtista(
                "duas-antigo@rf11.test", true, LocalDate.of(1990, 1, 1), teatro, musica);
        PerfilArtista duasFuncoesNovo = criarArtista(
                "duas-novo@rf11.test", true, LocalDate.of(1990, 1, 1), teatro, musica);
        PerfilArtista umaFuncao = criarArtista(
                "uma@rf11.test", true, LocalDate.of(1990, 1, 1), teatro);
        duasFuncoesAntigo.setUltimaAtualizacao(LocalDateTime.now().minusDays(2));
        duasFuncoesNovo.setUltimaAtualizacao(LocalDateTime.now());
        umaFuncao.setUltimaAtualizacao(LocalDateTime.now().plusDays(1));
        perfilArtistaRepository.saveAll(Set.of(duasFuncoesAntigo, duasFuncoesNovo, umaFuncao));

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", bearer(dono.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.talentosSugeridos.content[0].artistaId").value(duasFuncoesNovo.getUsuarioId()))
                .andExpect(jsonPath("$.talentosSugeridos.content[1].artistaId").value(duasFuncoesAntigo.getUsuarioId()))
                .andExpect(jsonPath("$.talentosSugeridos.content[2].artistaId").value(umaFuncao.getUsuarioId()));
    }

    @Test
    void notificacoesRf23EMensagensRf24EstaoDisponiveis() throws Exception {
        PerfilArtista artista = criarArtista("modulos@rf11.test", true, LocalDate.of(1990, 1, 1));

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", bearer(artista.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificacoes.disponivel").value(true))
                .andExpect(jsonPath("$.notificacoes.mensagem").value(
                        org.hamcrest.Matchers.containsString("tempo real")))
                .andExpect(jsonPath("$.notificacoes.quantidade").doesNotExist())
                .andExpect(jsonPath("$.mensagens.disponivel").value(true))
                .andExpect(jsonPath("$.mensagens.mensagem").value(
                        org.hamcrest.Matchers.containsString("privacidade")))
                .andExpect(jsonPath("$.mensagens.quantidadeNaoLidas").value(0));
    }

    @Test
    void paginacaoInformaTotalEHasMore() throws Exception {
        PerfilArtista artista = criarArtista("paginacao@rf11.test", true, LocalDate.of(1990, 1, 1));
        Funcao funcao = criarFuncao("Paginada");
        com.portifolio.support.OfficialSchemaFixtures.funcoes(artista, new HashSet<>(Set.of(funcao)));
        perfilArtistaRepository.save(artista);
        PerfilContratante contratante = criarContratante("paginador@rf11.test", true);
        IntStream.range(0, 3).forEach(i -> criarVaga(contratante, "Vaga " + i, StatusVaga.ABERTA, funcao));

        mockMvc.perform(get("/api/dashboard").param("size", "2")
                        .header("Authorization", bearer(artista.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vagasRecomendadas.content.length()").value(2))
                .andExpect(jsonPath("$.vagasRecomendadas.totalElements").value(3))
                .andExpect(jsonPath("$.vagasRecomendadas.hasMore").value(true));
    }

    @Test
    void vinteRecomendacoesNaoDisparamNMaisUm() throws Exception {
        PerfilArtista artista = criarArtista("nmaisum@rf11.test", true, LocalDate.of(1990, 1, 1));
        Funcao funcao = criarFuncao("Escalavel");
        com.portifolio.support.OfficialSchemaFixtures.funcoes(artista, new HashSet<>(Set.of(funcao)));
        perfilArtistaRepository.save(artista);
        PerfilContratante contratante = criarContratante("volume@rf11.test", true);
        IntStream.range(0, 20).forEach(i -> criarVaga(contratante, "Vaga volume " + i, StatusVaga.ABERTA, funcao));
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/dashboard").param("size", "20")
                        .header("Authorization", bearer(artista.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vagasRecomendadas.content.length()").value(20));

        // RF24 acrescenta uma unica consulta agregada para mensagens nao lidas;
        // o teto continua constante, independentemente das 20 recomendacoes.
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(7);
    }

    @Test
    void vinteCandidaturasEVinteTalentosNaoDisparamNMaisUm() throws Exception {
        PerfilContratante dono = criarContratante("volume-contratante@rf11.test", true);
        Funcao funcao = criarFuncao("Volume contratante");
        Vaga vaga = criarVaga(dono, "Vaga para vinte", StatusVaga.ABERTA, funcao);
        IntStream.range(0, 20).forEach(i -> {
            PerfilArtista artista = criarArtista(
                    "volume-artista-" + i + "@rf11.test",
                    true,
                    LocalDate.of(1990, 1, 1),
                    funcao);
            criarCandidatura(vaga, artista, LocalDateTime.now().minusMinutes(i));
        });
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/dashboard").param("size", "20")
                        .header("Authorization", bearer(dono.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidaturasRecentes.content.length()").value(20))
                .andExpect(jsonPath("$.talentosSugeridos.content.length()").value(20));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(10);
    }

    private Usuario criarUsuario(String email, TipoUsuario tipo, boolean completo, LocalDate nascimento) {
        Usuario usuario = new Usuario();
        usuario.setNome("Pessoa " + email.substring(0, email.indexOf('@')));
        usuario.setDataNascimento(nascimento);
        usuario.setTelefone("11999999999");
        usuario.setEmail(email);
        usuario.setSenha("hash-privado");
        usuario.setTipoUsuario(tipo);
        usuario.setStatusConta(com.portifolio.model.enums.StatusConta.ATIVA);
        usuario.setPerfilCompleto(completo);
        usuario.setDataCriacao(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    private PerfilArtista criarArtista(String email, boolean completo, LocalDate nascimento, Funcao... funcoes) {
        PerfilArtista perfil = new PerfilArtista();
        perfil.setTipoPerfilArtistico(com.portifolio.model.enums.TipoPerfilArtistico.ARTISTA_SOLO);
        perfil.setRaioAtuacao(com.portifolio.model.enums.Abrangencia.LOCAL);
        perfil.setUsuario(criarUsuario(email, TipoUsuario.ARTISTA, completo, nascimento));
        perfil.setBiografia("Biografia publica");
        perfil.setLocalizacao("Sao Paulo - SP");
        perfil.setUrlPortfolio("https://portfolio.example/" + email);
        com.portifolio.support.OfficialSchemaFixtures.funcoes(perfil, new HashSet<>(Set.of(funcoes)));
        return perfilArtistaRepository.save(perfil);
    }

    private PerfilContratante criarContratante(String email, boolean completo) {
        PerfilContratante perfil = new PerfilContratante();
        perfil.setUsuario(criarUsuario(email, TipoUsuario.CONTRATANTE, completo, LocalDate.of(1985, 1, 1)));
        perfil.setNomeEmpresa("Empresa " + email.substring(0, email.indexOf('@')));
        return perfilContratanteRepository.save(perfil);
    }

    private Funcao criarFuncao(String nome) {
        Funcao funcao = new Funcao();
        funcao.setArea(com.portifolio.support.OfficialSchemaFixtures.area());
        funcao.setNome(nome);
        return funcaoRepository.save(funcao);
    }

    private Vaga criarVaga(PerfilContratante contratante, String titulo, StatusVaga status, Funcao... funcoes) {
        Vaga vaga = new Vaga();
        vaga.setArea(com.portifolio.support.OfficialSchemaFixtures.area());
        vaga.setAbrangencia(com.portifolio.model.enums.Abrangencia.LOCAL);
        vaga.setContratante(contratante);
        vaga.setTitulo(titulo);
        vaga.setDescricao("Descricao da vaga");
        vaga.setRequisitos("Requisitos da vaga");
        vaga.setValorMinimo(new BigDecimal("1000.00"));
        vaga.setValorMaximo(new BigDecimal("1000.00"));
        vaga.setFormaRemuneracao(com.portifolio.model.enums.FormaRemuneracao.POR_EVENTO);
        vaga.setCidade("Sao Paulo");
        vaga.setEstado("SP");
        vaga.setModeloTrabalho(ModeloTrabalho.PRESENCIAL);
        vaga.setTipoContrato("Freelancer");
        vaga.setStatus(status);
        vaga.setDataPublicacao(LocalDateTime.now());
        vaga.setFuncoes(new HashSet<>(Set.of(funcoes)));
        return vagaRepository.save(vaga);
    }

    private Candidatura criarCandidatura(Vaga vaga, PerfilArtista artista, LocalDateTime data) {
        Candidatura candidatura = new Candidatura();
        candidatura.setVaga(vaga);
        candidatura.setArtista(artista);
        candidatura.setMensagemApresentacao("Mensagem privada");
        candidatura.setLinkPortfolioCandidatura("https://privado.example/portfolio");
        candidatura.setStatus(StatusCandidatura.PENDENTE);
        candidatura.setDataCandidatura(data);
        return candidaturaRepository.save(candidatura);
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.gerarToken(usuario);
    }
}
