package com.portifolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portifolio.model.Candidatura;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Usuario;
import com.portifolio.model.Vaga;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CandidaturaControllerRf06IntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScript("db/schema-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PerfilArtistaRepository perfilArtistaRepository;
    @Autowired PerfilContratanteRepository perfilContratanteRepository;
    @Autowired VagaRepository vagaRepository;
    @Autowired CandidaturaRepository candidaturaRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void limparBanco() {
        jdbcTemplate.execute(
                "TRUNCATE candidaturas, vagas, perfis_artistas, perfis_contratantes, usuarios RESTART IDENTITY CASCADE");
    }

    @Test
    void criacaoExigeAutenticacao() throws Exception {
        mockMvc.perform(post("/api/candidaturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCriacao(1L, 999L, null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void artistaDoJwtCriaCandidaturaPendenteEIdsDoClienteSaoIgnorados() throws Exception {
        PerfilContratante contratante = novoContratante("contratante-criacao@teste.com");
        Vaga vaga = novaVaga(contratante, StatusVaga.ABERTA);
        PerfilArtista autenticado = novoArtista("artista-autenticado@teste.com", true);
        PerfilArtista outro = novoArtista("artista-outro@teste.com", true);

        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", bearer(autenticado.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCriacao(vaga.getId(), outro.getUsuarioId(), "APROVADO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.artistaId").value(autenticado.getUsuarioId()))
                .andExpect(jsonPath("$.status").value("PENDENTE"));

        Candidatura salva = candidaturaRepository.findAll().getFirst();
        assertThat(salva.getArtista().getUsuarioId()).isEqualTo(autenticado.getUsuarioId());
        assertThat(salva.getDataCandidatura()).isNotNull();
    }

    @Test
    void contratanteNaoPodeCriarCandidatura() throws Exception {
        PerfilContratante contratante = novoContratante("contratante-nao-artista@teste.com");
        Vaga vaga = novaVaga(contratante, StatusVaga.ABERTA);

        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", bearer(contratante.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCriacao(vaga.getId(), contratante.getUsuarioId(), null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void perfilIncompletoRetorna422() throws Exception {
        PerfilContratante contratante = novoContratante("contratante-incompleto@teste.com");
        Vaga vaga = novaVaga(contratante, StatusVaga.ABERTA);
        PerfilArtista artista = novoArtista("artista-incompleto@teste.com", false);

        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCriacao(vaga.getId(), artista.getUsuarioId(), null)))
                .andExpect(status().isUnprocessableEntity());
    }

    @ParameterizedTest
    @MethodSource("statusDeVagaIndisponiveis")
    void vagaNaoAbertaRetorna422(StatusVaga statusVaga) throws Exception {
        PerfilContratante contratante = novoContratante(
                "contratante-" + statusVaga + "@teste.com");
        Vaga vaga = novaVaga(contratante, statusVaga);
        PerfilArtista artista = novoArtista("artista-" + statusVaga + "@teste.com", true);

        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCriacao(vaga.getId(), artista.getUsuarioId(), null)))
                .andExpect(status().isUnprocessableEntity());
    }

    static Stream<StatusVaga> statusDeVagaIndisponiveis() {
        return Stream.of(StatusVaga.PAUSADA, StatusVaga.ENCERRADA, StatusVaga.CANCELADA);
    }

    @Test
    void candidaturaDuplicadaRetorna409() throws Exception {
        PerfilContratante contratante = novoContratante("contratante-duplicada@teste.com");
        Vaga vaga = novaVaga(contratante, StatusVaga.ABERTA);
        PerfilArtista artista = novoArtista("artista-duplicada@teste.com", true);
        novaCandidatura(vaga, artista, StatusCandidatura.PENDENTE);

        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCriacao(vaga.getId(), artista.getUsuarioId(), null)))
                .andExpect(status().isConflict());
    }

    @Test
    void mensagemELinkInvalidosRetornam400() throws Exception {
        PerfilContratante contratante = novoContratante("contratante-validacao@teste.com");
        Vaga vaga = novaVaga(contratante, StatusVaga.ABERTA);
        PerfilArtista artista = novoArtista("artista-validacao@teste.com", true);
        String corpo = """
                {"vagaId":%d,"mensagemApresentacao":"%s",
                "linkPortfolioCandidatura":"nao-e-url"}
                """.formatted(vaga.getId(), "x".repeat(2001));

        mockMvc.perform(post("/api/candidaturas")
                        .header("Authorization", bearer(artista.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes.length()").value(2));
    }

    @Test
    void artistaListaEDetalhaSomentePropriasCandidaturasComPaginacaoLimitada() throws Exception {
        PerfilContratante contratante = novoContratante("contratante-consulta@teste.com");
        Vaga vaga = novaVaga(contratante, StatusVaga.ABERTA);
        PerfilArtista artista = novoArtista("artista-consulta@teste.com", true);
        PerfilArtista outro = novoArtista("outro-consulta@teste.com", true);
        Candidatura propria = novaCandidatura(vaga, artista, StatusCandidatura.PENDENTE);
        Candidatura alheia = novaCandidatura(vaga, outro, StatusCandidatura.PENDENTE);

        mockMvc.perform(get("/api/candidaturas?tamanho=999")
                        .header("Authorization", bearer(artista.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Page-Size", "50"))
                .andExpect(header().string("X-Total-Elements", "1"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(propria.getId()));
        mockMvc.perform(get("/api/candidaturas/{id}", alheia.getId())
                        .header("Authorization", bearer(artista.getUsuario())))
                .andExpect(status().isNotFound());
    }

    @Test
    void contratanteVeEAlteraSomenteCandidaturasDasPropriasVagas() throws Exception {
        PerfilContratante dono = novoContratante("dono-consulta@teste.com");
        PerfilContratante intruso = novoContratante("intruso-consulta@teste.com");
        PerfilArtista artista = novoArtista("artista-consulta-dono@teste.com", true);
        Candidatura candidatura = novaCandidatura(
                novaVaga(dono, StatusVaga.ABERTA), artista, StatusCandidatura.PENDENTE);

        mockMvc.perform(get("/api/candidaturas/minhas-vagas")
                        .header("Authorization", bearer(dono.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(candidatura.getId()));
        atualizarStatus(candidatura, intruso.getUsuario(), StatusCandidatura.EM_ANALISE)
                .andExpect(status().isForbidden());
        atualizarStatus(candidatura, dono.getUsuario(), StatusCandidatura.EM_ANALISE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANALISE"));
    }

    @Test
    void artistaNaoManipulaResultadoERetiradaEhLogica() throws Exception {
        PerfilContratante dono = novoContratante("dono-retirada@teste.com");
        PerfilArtista artista = novoArtista("artista-retirada@teste.com", true);
        Candidatura candidatura = novaCandidatura(
                novaVaga(dono, StatusVaga.ABERTA), artista, StatusCandidatura.PENDENTE);

        atualizarStatus(candidatura, artista.getUsuario(), StatusCandidatura.APROVADO)
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/candidaturas/{id}", candidatura.getId())
                        .header("Authorization", bearer(artista.getUsuario())))
                .andExpect(status().isNoContent());

        assertThat(candidaturaRepository.findById(candidatura.getId())).isPresent()
                .get().extracting(Candidatura::getStatus).isEqualTo(StatusCandidatura.RETIRADA);
    }

    private Usuario novoUsuario(String email, TipoUsuario tipo) {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuário RF06");
        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setTelefone("11999999999");
        usuario.setEmail(email);
        usuario.setSenha("{noop}senha-teste");
        usuario.setTipoUsuario(tipo);
        usuario.setPerfilCompleto(false);
        usuario.setDataCriacao(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    private PerfilContratante novoContratante(String email) {
        PerfilContratante perfil = new PerfilContratante();
        perfil.setUsuario(novoUsuario(email, TipoUsuario.CONTRATANTE));
        perfil.setNomeEmpresa("Empresa RF06");
        return perfilContratanteRepository.save(perfil);
    }

    private PerfilArtista novoArtista(String email, boolean completo) {
        Usuario usuario = novoUsuario(email, TipoUsuario.ARTISTA);
        usuario.setPerfilCompleto(completo);
        usuarioRepository.save(usuario);
        PerfilArtista perfil = new PerfilArtista();
        perfil.setUsuario(usuario);
        perfil.setBiografia("Biografia RF06");
        return perfilArtistaRepository.save(perfil);
    }

    private Vaga novaVaga(PerfilContratante contratante, StatusVaga status) {
        Vaga vaga = new Vaga();
        vaga.setContratante(contratante);
        vaga.setTitulo("Vaga RF06 " + status);
        vaga.setDescricao("Descrição da vaga");
        vaga.setRequisitos("Requisitos da vaga");
        vaga.setRemuneraValor(new BigDecimal("1000.00"));
        vaga.setFormaPagamento("Pix");
        vaga.setCidade("São Paulo");
        vaga.setEstado("SP");
        vaga.setModeloTrabalho(ModeloTrabalho.REMOTO);
        vaga.setTipoContrato("Freelance");
        vaga.setStatus(status);
        vaga.setDataPublicacao(LocalDateTime.now());
        vaga.setTags(new HashSet<>());
        return vagaRepository.save(vaga);
    }

    private Candidatura novaCandidatura(
            Vaga vaga, PerfilArtista artista, StatusCandidatura status) {
        Candidatura candidatura = new Candidatura();
        candidatura.setVaga(vaga);
        candidatura.setArtista(artista);
        candidatura.setMensagemApresentacao("Tenho interesse nesta oportunidade.");
        candidatura.setLinkPortfolioCandidatura("https://exemplo.com/portfolio");
        candidatura.setStatus(status);
        candidatura.setDataCandidatura(LocalDateTime.now());
        return candidaturaRepository.save(candidatura);
    }

    private String corpoCriacao(Long vagaId, Long artistaId, String status) {
        String campoStatus = status == null ? "" : ",\"status\":\"" + status + "\"";
        return """
                {"vagaId":%d,"artistaId":%d,"mensagemApresentacao":"Tenho interesse nesta oportunidade.",
                "linkPortfolioCandidatura":"https://exemplo.com/portfolio"%s}
                """.formatted(vagaId, artistaId, campoStatus);
    }

    private org.springframework.test.web.servlet.ResultActions atualizarStatus(
            Candidatura candidatura, Usuario ator, StatusCandidatura status) throws Exception {
        String corpo = """
                {"status":"%s","artistaId":999999,"vagaId":999999,
                "mensagemApresentacao":"campo protegido"}
                """.formatted(status);
        return mockMvc.perform(put("/api/candidaturas/{id}", candidatura.getId())
                .header("Authorization", bearer(ator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.gerarToken(usuario);
    }
}
