package com.portifolio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class VagaEdicaoRf07IntegrationTest {

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
    @Autowired FuncaoRepository funcaoRepository;
    @Autowired CandidaturaRepository candidaturaRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void limparBanco() {
        jdbcTemplate.execute("TRUNCATE candidaturas, vagas, funcoes, perfis_artistas, "
                + "perfis_contratantes, usuarios RESTART IDENTITY CASCADE");
    }

    @Test
    void naoAutenticadoDeveReceber401() throws Exception {
        mockMvc.perform(put("/api/vagas/{id}", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payloadValido())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void artistaDeveReceber403() throws Exception {
        Usuario dono = criarUsuario("dono-artista@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = criarVaga(criarContratante(dono), StatusVaga.ABERTA);
        Usuario artista = criarUsuario("artista-rf07@teste.com", TipoUsuario.ARTISTA);
        criarArtista(artista);

        editar(vaga.getId(), artista, payloadValido())
                .andExpect(status().isForbidden());
    }

    @Test
    void contratanteNaoProprietarioDeveReceber403() throws Exception {
        Usuario dono = criarUsuario("dono-idor@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = criarVaga(criarContratante(dono), StatusVaga.ABERTA);
        Usuario intruso = criarUsuario("intruso@teste.com", TipoUsuario.CONTRATANTE);
        criarContratante(intruso);

        ObjectNode payload = payloadValido();
        payload.put("contratanteId", intruso.getId());
        editar(vaga.getId(), intruso, payload)
                .andExpect(status().isForbidden());

        assertThat(vagaRepository.findById(vaga.getId()).orElseThrow().getTitulo()).isEqualTo("Original");
    }

    @Test
    void vagaInexistenteDeveReceber404() throws Exception {
        Usuario contratante = criarUsuario("inexistente@teste.com", TipoUsuario.CONTRATANTE);
        criarContratante(contratante);

        editar(999_999L, contratante, payloadValido())
                .andExpect(status().isNotFound());
    }

    @Test
    void proprietarioDeveAtualizarTodosOsDetalhesEPreservarCamposProtegidos() throws Exception {
        Usuario dono = criarUsuario("dono-campos@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = criarVaga(criarContratante(dono), StatusVaga.PAUSADA);
        Long idOriginal = vaga.getId();
        LocalDateTime publicacaoOriginal = vaga.getDataPublicacao();
        Usuario outro = criarUsuario("outro-id-payload@teste.com", TipoUsuario.CONTRATANTE);
        criarContratante(outro);

        ObjectNode payload = payloadValido();
        payload.put("contratanteId", outro.getId());
        payload.put("id", 987654);
        payload.put("status", "CANCELADA");

        editar(idOriginal, dono, payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idOriginal))
                .andExpect(jsonPath("$.contratanteId").value(dono.getId()))
                .andExpect(jsonPath("$.status").value("PAUSADA"))
                .andExpect(jsonPath("$.titulo").value("Título atualizado"))
                .andExpect(jsonPath("$.descricao").value("Descrição atualizada"))
                .andExpect(jsonPath("$.requisitos").value("Requisitos atualizados"))
                .andExpect(jsonPath("$.remuneraValor").value(2500.75))
                .andExpect(jsonPath("$.cidade").value("Campinas"))
                .andExpect(jsonPath("$.estado").value("SP"))
                .andExpect(jsonPath("$.modeloTrabalho").value("HIBRIDO"))
                .andExpect(jsonPath("$.fotos.length()").value(2));

        Vaga persistida = vagaRepository.findDetalhesById(idOriginal).orElseThrow();
        assertThat(persistida.getId()).isEqualTo(idOriginal);
        assertThat(persistida.getContratante().getUsuarioId()).isEqualTo(dono.getId());
        assertThat(persistida.getStatus()).isEqualTo(StatusVaga.PAUSADA);
        assertThat(persistida.getDataPublicacao()).isEqualTo(publicacaoOriginal);
        assertThat(persistida.getFormaRemuneracao()).isEqualTo(com.portifolio.model.enums.FormaRemuneracao.POR_EVENTO);
        assertThat(persistida.getEnderecoCompleto()).isEqualTo("Rua Nova, 10");
        assertThat(persistida.getBeneficios()).isEqualTo("Transporte");
        assertThat(persistida.getTipoContrato()).isEqualTo("Temporário");
        assertThat(persistida.getArea().getNome()).isEqualTo("Música");
        assertThat(persistida.getExperiencia()).isEqualTo("Pleno");
        assertThat(persistida.getDataLimiteCandidatura()).isEqualTo(LocalDate.of(2030, 12, 20));
        assertThat(persistida.getAbrangencia()).isEqualTo(com.portifolio.model.enums.Abrangencia.NACIONAL);
    }

    @ParameterizedTest
    @EnumSource(value = StatusVaga.class, names = {"PAUSADA", "ENCERRADA", "CANCELADA"})
    void edicaoDeDetalhesNaoDeveInventarRestricaoPorStatus(StatusVaga statusOriginal) throws Exception {
        Usuario dono = criarUsuario("status-" + statusOriginal + "@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = criarVaga(criarContratante(dono), statusOriginal);
        ObjectNode payload = payloadValido();
        payload.put("status", "ABERTA");

        editar(vaga.getId(), dono, payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(statusOriginal.name()));
    }

    @Test
    void deveRejeitarObrigatorioBlank() throws Exception {
        Usuario dono = criarUsuario("blank@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = criarVaga(criarContratante(dono), StatusVaga.ABERTA);
        ObjectNode payload = payloadValido();
        payload.put("titulo", "   ");

        editar(vaga.getId(), dono, payload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes[0]").isNotEmpty());
    }

    @Test
    void deveRejeitarLimitesAntesDoPostgresql() throws Exception {
        Usuario dono = criarUsuario("limites@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = criarVaga(criarContratante(dono), StatusVaga.ABERTA);

        ObjectNode tituloLongo = payloadValido();
        tituloLongo.put("titulo", "x".repeat(151));
        editar(vaga.getId(), dono, tituloLongo).andExpect(status().isBadRequest());

        ObjectNode valorForaDaPrecisao = payloadValido();
        valorForaDaPrecisao.put("areaId", 1);
        valorForaDaPrecisao.put("abrangencia", "LOCAL");
        valorForaDaPrecisao.put("valorMinimo", 100_000_000);
        valorForaDaPrecisao.put("valorMaximo", 100_000_000);
        editar(vaga.getId(), dono, valorForaDaPrecisao).andExpect(status().isBadRequest());

        assertThat(vagaRepository.findById(vaga.getId()).orElseThrow().getTitulo()).isEqualTo("Original");
    }

    @Test
    void deveRejeitarRemuneracaoNegativaEEnumInvalido() throws Exception {
        Usuario dono = criarUsuario("formato@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = criarVaga(criarContratante(dono), StatusVaga.ABERTA);

        ObjectNode negativo = payloadValido();
        negativo.put("areaId", 1);
        negativo.put("abrangencia", "LOCAL");
        negativo.put("valorMinimo", -0.01);
        negativo.put("valorMaximo", -0.01);
        editar(vaga.getId(), dono, negativo).andExpect(status().isBadRequest());

        ObjectNode enumInvalido = payloadValido();
        enumInvalido.put("modeloTrabalho", "INVALIDO");
        editar(vaga.getId(), dono, enumInvalido).andExpect(status().isBadRequest());
    }

    @Test
    void deveSubstituirAdicionarRemoverEDeduplicarFuncoes() throws Exception {
        Usuario dono = criarUsuario("funcoes@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = criarVaga(criarContratante(dono), StatusVaga.ABERTA);
        Funcao tag1 = criarFuncao("Música");
        Funcao tag2 = criarFuncao("Fotografia");
        Funcao tag3 = criarFuncao("Teatro");
        definirFuncoes(vaga.getId(), tag1.getId(), tag2.getId());

        ObjectNode substituir = payloadValido();
        substituir.putArray("funcaoIds").add(tag2.getId()).add(tag3.getId()).add(tag3.getId());
        editar(vaga.getId(), dono, substituir).andExpect(status().isOk());
        assertThat(tagsDaVaga(vaga.getId())).containsExactlyInAnyOrder(tag2.getId(), tag3.getId());

        ObjectNode adicionar = payloadValido();
        adicionar.putArray("funcaoIds").add(tag1.getId()).add(tag2.getId()).add(tag3.getId());
        editar(vaga.getId(), dono, adicionar).andExpect(status().isOk());
        assertThat(tagsDaVaga(vaga.getId())).containsExactlyInAnyOrder(tag1.getId(), tag2.getId(), tag3.getId());

        ObjectNode remover = payloadValido();
        remover.putArray("funcaoIds").add(tag1.getId());
        editar(vaga.getId(), dono, remover).andExpect(status().isOk());
        assertThat(tagsDaVaga(vaga.getId())).containsExactly(tag1.getId());
    }

    @Test
    void funcaoIdsAusenteOuNullDevePreservarEListaVaziaDeveLimpar() throws Exception {
        Usuario dono = criarUsuario("semantica-funcoes@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = criarVaga(criarContratante(dono), StatusVaga.ABERTA);
        Funcao funcao = criarFuncao("Dança");
        definirFuncoes(vaga.getId(), funcao.getId());

        ObjectNode ausente = payloadValido();
        ausente.remove("funcaoIds");
        editar(vaga.getId(), dono, ausente).andExpect(status().isOk());
        assertThat(tagsDaVaga(vaga.getId())).containsExactly(funcao.getId());

        ObjectNode nulo = payloadValido();
        nulo.putNull("funcaoIds");
        editar(vaga.getId(), dono, nulo).andExpect(status().isOk());
        assertThat(tagsDaVaga(vaga.getId())).containsExactly(funcao.getId());

        ObjectNode vazio = payloadValido();
        vazio.putArray("funcaoIds");
        editar(vaga.getId(), dono, vazio).andExpect(status().isOk());
        assertThat(tagsDaVaga(vaga.getId())).isEmpty();
    }

    @Test
    void funcaoInexistenteDeveCausarRollbackSemAlterarCatalogoOuFuncoesDoArtista() throws Exception {
        Usuario dono = criarUsuario("rollback@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = criarVaga(criarContratante(dono), StatusVaga.ABERTA);
        Funcao tag1 = criarFuncao("Cinema");
        Funcao tag2 = criarFuncao("Produção");
        definirFuncoes(vaga.getId(), tag1.getId(), tag2.getId());

        Usuario artistaUsuario = criarUsuario("artista-funcao@teste.com", TipoUsuario.ARTISTA);
        PerfilArtista artista = criarArtista(artistaUsuario);
        com.portifolio.support.OfficialSchemaFixtures.funcoes(artista, Set.of(tag1));
        perfilArtistaRepository.save(artista);
        long catalogoAntes = funcaoRepository.count();
        long tagsArtistaAntes = contarFuncoesArtista(artistaUsuario.getId());
        assertThat(tagsArtistaAntes).isEqualTo(1);

        ObjectNode payload = payloadValido();
        payload.put("titulo", "Não pode persistir");
        payload.putArray("funcaoIds").add(tag1.getId()).add(999_999);
        editar(vaga.getId(), dono, payload).andExpect(status().isNotFound());

        assertThat(vagaRepository.findById(vaga.getId()).orElseThrow().getTitulo()).isEqualTo("Original");
        assertThat(tagsDaVaga(vaga.getId())).containsExactlyInAnyOrder(tag1.getId(), tag2.getId());
        assertThat(funcaoRepository.count()).isEqualTo(catalogoAntes);
        assertThat(contarFuncoesArtista(artistaUsuario.getId())).isEqualTo(tagsArtistaAntes);
    }

    @Test
    void candidaturaDevePermanecerIntegralmenteIntacta() throws Exception {
        Usuario dono = criarUsuario("candidatura-dono@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = criarVaga(criarContratante(dono), StatusVaga.ABERTA);
        Usuario artistaUsuario = criarUsuario("candidatura-artista@teste.com", TipoUsuario.ARTISTA);
        PerfilArtista artista = criarArtista(artistaUsuario);
        Candidatura original = criarCandidatura(vaga, artista);

        editar(vaga.getId(), dono, payloadValido()).andExpect(status().isOk());

        List<Candidatura> candidaturas = candidaturaRepository.findByVagaId(vaga.getId());
        assertThat(candidaturas).hasSize(1);
        Candidatura persistida = candidaturas.get(0);
        assertThat(persistida.getId()).isEqualTo(original.getId());
        assertThat(persistida.getVaga().getId()).isEqualTo(vaga.getId());
        assertThat(persistida.getArtista().getUsuarioId()).isEqualTo(artistaUsuario.getId());
        assertThat(persistida.getStatus()).isEqualTo(original.getStatus());
        assertThat(persistida.getMensagemApresentacao()).isEqualTo(original.getMensagemApresentacao());
        assertThat(persistida.getLinkPortfolioCandidatura()).isEqualTo(original.getLinkPortfolioCandidatura());
        assertThat(persistida.getDataCandidatura()).isEqualTo(original.getDataCandidatura());
    }

    private org.springframework.test.web.servlet.ResultActions editar(
            Long vagaId, Usuario usuario, ObjectNode payload) throws Exception {
        return mockMvc.perform(put("/api/vagas/{id}", vagaId)
                .header("Authorization", "Bearer " + jwtService.gerarToken(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));
    }

    private ObjectNode payloadValido() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("titulo", "Título atualizado");
        payload.put("descricao", "Descrição atualizada");
        payload.put("requisitos", "Requisitos atualizados");
        payload.put("areaId", 1);
        payload.put("abrangencia", "LOCAL");
        payload.put("valorMinimo", 2500.75);
        payload.put("valorMaximo", 2500.75);
        payload.put("formaRemuneracao", "POR_EVENTO");
        payload.put("cidade", "Campinas");
        payload.put("estado", "SP");
        payload.put("enderecoCompleto", "Rua Nova, 10");
        payload.put("beneficios", "Transporte");
        payload.put("modeloTrabalho", "HIBRIDO");
        payload.put("tipoContrato", "Temporário");
        payload.put("categoria", "Música");
        payload.put("experiencia", "Pleno");
        payload.put("dataLimiteCandidatura", "2030-12-20");
        payload.put("abrangencia", "NACIONAL");
        payload.putArray("fotos").add("assets/vaga-foto-1.png").add("https://exemplo.com/foto.jpg");
        return payload;
    }

    private Usuario criarUsuario(String email, TipoUsuario tipo) {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuário RF07");
        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setTelefone("11999999999");
        usuario.setEmail(email);
        usuario.setSenha("{noop}senha-teste");
        usuario.setTipoUsuario(tipo);
        usuario.setPerfilCompleto(tipo == TipoUsuario.ARTISTA);
        usuario.setDataCriacao(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    private PerfilContratante criarContratante(Usuario usuario) {
        PerfilContratante perfil = new PerfilContratante();
        perfil.setUsuario(usuario);
        perfil.setNomeEmpresa("Empresa RF07");
        return perfilContratanteRepository.save(perfil);
    }

    private PerfilArtista criarArtista(Usuario usuario) {
        PerfilArtista perfil = new PerfilArtista();
        perfil.setTipoPerfilArtistico(com.portifolio.model.enums.TipoPerfilArtistico.ARTISTA_SOLO);
        perfil.setRaioAtuacao(com.portifolio.model.enums.Abrangencia.LOCAL);
        perfil.setUsuario(usuario);
        perfil.setBiografia("Biografia RF07");
        return perfilArtistaRepository.save(perfil);
    }

    private Vaga criarVaga(PerfilContratante contratante, StatusVaga status) {
        Vaga vaga = new Vaga();
        vaga.setArea(com.portifolio.support.OfficialSchemaFixtures.area());
        vaga.setAbrangencia(com.portifolio.model.enums.Abrangencia.LOCAL);
        vaga.setContratante(contratante);
        vaga.setTitulo("Original");
        vaga.setDescricao("Descrição original");
        vaga.setRequisitos("Requisitos originais");
        vaga.setValorMinimo(new BigDecimal("1000.00"));
        vaga.setValorMaximo(new BigDecimal("1000.00"));
        vaga.setFormaRemuneracao(com.portifolio.model.enums.FormaRemuneracao.POR_EVENTO);
        vaga.setCidade("São Paulo");
        vaga.setEstado("SP");
        vaga.setModeloTrabalho(ModeloTrabalho.REMOTO);
        vaga.setTipoContrato("Freelance");
        vaga.setStatus(status);
        vaga.setDataPublicacao(LocalDateTime.of(2026, 1, 15, 10, 30));
        vaga.setFuncoes(new HashSet<>());
        return vagaRepository.save(vaga);
    }

    private Funcao criarFuncao(String nome) {
        Funcao funcao = new Funcao();
        funcao.setArea(com.portifolio.support.OfficialSchemaFixtures.area());
        funcao.setNome(nome);
        return funcaoRepository.save(funcao);
    }

    private void definirFuncoes(Long vagaId, Long... funcaoIds) {
        for (Long funcaoId : funcaoIds) {
            jdbcTemplate.update("INSERT INTO vaga_funcao (vaga_id, funcao_id) VALUES (?, ?)", vagaId, funcaoId);
        }
    }

    private List<Long> tagsDaVaga(Long vagaId) {
        return jdbcTemplate.queryForList(
                "SELECT funcao_id FROM vaga_funcao WHERE vaga_id = ? ORDER BY funcao_id", Long.class, vagaId);
    }

    private long contarFuncoesArtista(Long artistaId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM perfil_artista_funcao WHERE perfil_artista_id = ?", Long.class, artistaId);
    }

    private Candidatura criarCandidatura(Vaga vaga, PerfilArtista artista) {
        Candidatura candidatura = new Candidatura();
        candidatura.setVaga(vaga);
        candidatura.setArtista(artista);
        candidatura.setMensagemApresentacao("Mensagem original");
        candidatura.setLinkPortfolioCandidatura("https://exemplo.com/portfolio-original");
        candidatura.setStatus(StatusCandidatura.EM_ANALISE);
        candidatura.setDataCandidatura(LocalDateTime.of(2026, 8, 10, 14, 15, 30));
        return candidaturaRepository.save(candidatura);
    }
}
