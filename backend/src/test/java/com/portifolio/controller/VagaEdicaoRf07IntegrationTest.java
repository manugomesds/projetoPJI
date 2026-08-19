package com.portifolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portifolio.model.Candidatura;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Tag;
import com.portifolio.model.Usuario;
import com.portifolio.model.Vaga;
import com.portifolio.model.enums.ModeloTrabalho;
import com.portifolio.model.enums.StatusCandidatura;
import com.portifolio.model.enums.StatusVaga;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.CandidaturaRepository;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.TagRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.repository.VagaRepository;
import com.portifolio.security.JwtService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
class VagaEdicaoRf07IntegrationTest {

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
    @Autowired TagRepository tagRepository;
    @Autowired CandidaturaRepository candidaturaRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void limparBanco() {
        jdbcTemplate.execute("TRUNCATE candidaturas, vagas, tags, perfis_artistas, "
                + "perfis_contratantes, usuarios RESTART IDENTITY CASCADE");
    }

    @Test
    void edicaoExigeAutenticacao() throws Exception {
        mockMvc.perform(put("/api/vagas/{id}", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payloadValido())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void somenteContratanteProprietarioPodeEditar() throws Exception {
        Usuario dono = novoUsuario("dono-rf07@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = novaVaga(novoContratante(dono), StatusVaga.ABERTA);
        Usuario intruso = novoUsuario("intruso-rf07@teste.com", TipoUsuario.CONTRATANTE);
        novoContratante(intruso);
        Usuario artista = novoUsuario("artista-rf07@teste.com", TipoUsuario.ARTISTA);
        novoArtista(artista);

        editar(vaga.getId(), intruso, payloadValido()).andExpect(status().isForbidden());
        editar(vaga.getId(), artista, payloadValido()).andExpect(status().isForbidden());
        assertThat(vagaRepository.findById(vaga.getId()).orElseThrow().getTitulo())
                .isEqualTo("Original");
    }

    @Test
    void camposProtegidosSaoIgnoradosEStatusPersistidoEhPreservado() throws Exception {
        Usuario dono = novoUsuario("dono-protegidos@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = novaVaga(novoContratante(dono), StatusVaga.PAUSADA);
        LocalDateTime publicacaoOriginal = vaga.getDataPublicacao();
        Usuario outro = novoUsuario("outro-payload@teste.com", TipoUsuario.CONTRATANTE);
        novoContratante(outro);
        ObjectNode payload = payloadValido();
        payload.put("contratanteId", outro.getId());
        payload.put("id", 999999);
        payload.put("status", "CANCELADA");
        payload.put("dataPublicacao", "2035-01-01T00:00:00");

        editar(vaga.getId(), dono, payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vaga.getId()))
                .andExpect(jsonPath("$.contratanteId").value(dono.getId()))
                .andExpect(jsonPath("$.status").value("PAUSADA"));

        Vaga salva = vagaRepository.findById(vaga.getId()).orElseThrow();
        assertThat(salva.getContratante().getUsuarioId()).isEqualTo(dono.getId());
        assertThat(salva.getDataPublicacao()).isEqualTo(publicacaoOriginal);
        assertThat(salva.getStatus()).isEqualTo(StatusVaga.PAUSADA);
    }

    @Test
    void remuneracaoNegativaELimitesInvalidosRetornam400() throws Exception {
        Usuario dono = novoUsuario("dono-validacao-rf07@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = novaVaga(novoContratante(dono), StatusVaga.ABERTA);
        ObjectNode negativo = payloadValido();
        negativo.put("remuneraValor", -0.01);
        editar(vaga.getId(), dono, negativo).andExpect(status().isBadRequest());

        ObjectNode tituloLongo = payloadValido();
        tituloLongo.put("titulo", "x".repeat(151));
        editar(vaga.getId(), dono, tituloLongo).andExpect(status().isBadRequest());
        assertThat(vagaRepository.findById(vaga.getId()).orElseThrow().getTitulo())
                .isEqualTo("Original");
    }

    @Test
    void tagIdsNullPreservaEVazioRemoveVinculos() throws Exception {
        Usuario dono = novoUsuario("dono-tags-rf07@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = novaVaga(novoContratante(dono), StatusVaga.ABERTA);
        Tag tag = novaTag("Música");
        vincularTag(vaga.getId(), tag.getId());

        ObjectNode ausente = payloadValido();
        ausente.remove("tagIds");
        editar(vaga.getId(), dono, ausente).andExpect(status().isOk());
        assertThat(tagsDaVaga(vaga.getId())).containsExactly(tag.getId());

        ObjectNode nulo = payloadValido();
        nulo.putNull("tagIds");
        editar(vaga.getId(), dono, nulo).andExpect(status().isOk());
        assertThat(tagsDaVaga(vaga.getId())).containsExactly(tag.getId());

        ObjectNode vazio = payloadValido();
        vazio.putArray("tagIds");
        editar(vaga.getId(), dono, vazio).andExpect(status().isOk());
        assertThat(tagsDaVaga(vaga.getId())).isEmpty();
    }

    @Test
    void tagsSaoSubstituidasEDeduplicadas() throws Exception {
        Usuario dono = novoUsuario("dono-substitui-tags@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = novaVaga(novoContratante(dono), StatusVaga.ABERTA);
        Tag antiga = novaTag("Antiga");
        Tag nova = novaTag("Nova");
        vincularTag(vaga.getId(), antiga.getId());
        ObjectNode payload = payloadValido();
        payload.putArray("tagIds").add(nova.getId()).add(nova.getId());

        editar(vaga.getId(), dono, payload).andExpect(status().isOk());
        assertThat(tagsDaVaga(vaga.getId())).containsExactly(nova.getId());
    }

    @Test
    void tagInexistenteCausaRollbackIntegral() throws Exception {
        Usuario dono = novoUsuario("dono-rollback-rf07@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = novaVaga(novoContratante(dono), StatusVaga.ABERTA);
        Tag tag = novaTag("Original");
        vincularTag(vaga.getId(), tag.getId());
        ObjectNode payload = payloadValido();
        payload.put("titulo", "Não deve persistir");
        payload.putArray("tagIds").add(tag.getId()).add(999999);

        editar(vaga.getId(), dono, payload).andExpect(status().isNotFound());
        assertThat(vagaRepository.findById(vaga.getId()).orElseThrow().getTitulo())
                .isEqualTo("Original");
        assertThat(tagsDaVaga(vaga.getId())).containsExactly(tag.getId());
    }

    @Test
    void historicoDeCandidaturaPermaneceIntegralmenteIntacto() throws Exception {
        Usuario dono = novoUsuario("dono-candidatura-rf07@teste.com", TipoUsuario.CONTRATANTE);
        Vaga vaga = novaVaga(novoContratante(dono), StatusVaga.ABERTA);
        Usuario usuarioArtista = novoUsuario("artista-candidatura-rf07@teste.com", TipoUsuario.ARTISTA);
        PerfilArtista artista = novoArtista(usuarioArtista);
        Candidatura original = novaCandidatura(vaga, artista);

        editar(vaga.getId(), dono, payloadValido()).andExpect(status().isOk());

        List<Candidatura> candidaturas = candidaturaRepository.findByVagaId(vaga.getId());
        assertThat(candidaturas).hasSize(1);
        Candidatura salva = candidaturas.getFirst();
        assertThat(salva.getId()).isEqualTo(original.getId());
        assertThat(salva.getArtista().getUsuarioId()).isEqualTo(usuarioArtista.getId());
        assertThat(salva.getStatus()).isEqualTo(StatusCandidatura.EM_ANALISE);
        assertThat(salva.getMensagemApresentacao()).isEqualTo("Mensagem original");
        assertThat(salva.getLinkPortfolioCandidatura())
                .isEqualTo("https://exemplo.com/portfolio-original");
        assertThat(salva.getDataCandidatura()).isEqualTo(original.getDataCandidatura());
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
        payload.put("remuneraValor", 2500.75);
        payload.put("formaPagamento", "Transferência");
        payload.put("cidade", "Campinas");
        payload.put("estado", "SP");
        payload.put("enderecoCompleto", "Rua Nova, 10");
        payload.put("beneficios", "Transporte");
        payload.put("modeloTrabalho", "HIBRIDO");
        payload.put("tipoContrato", "Temporário");
        payload.put("categoria", "Música");
        payload.put("experiencia", "Pleno");
        payload.put("dataLimiteCandidatura", "2030-12-20");
        payload.put("abrangencia", "nacional");
        payload.putArray("fotos").add("assets/vaga-foto-1.png");
        return payload;
    }

    private Usuario novoUsuario(String email, TipoUsuario tipo) {
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

    private PerfilContratante novoContratante(Usuario usuario) {
        PerfilContratante perfil = new PerfilContratante();
        perfil.setUsuario(usuario);
        perfil.setNomeEmpresa("Empresa RF07");
        return perfilContratanteRepository.save(perfil);
    }

    private PerfilArtista novoArtista(Usuario usuario) {
        PerfilArtista perfil = new PerfilArtista();
        perfil.setUsuario(usuario);
        perfil.setBiografia("Biografia RF07");
        return perfilArtistaRepository.save(perfil);
    }

    private Vaga novaVaga(PerfilContratante contratante, StatusVaga status) {
        Vaga vaga = new Vaga();
        vaga.setContratante(contratante);
        vaga.setTitulo("Original");
        vaga.setDescricao("Descrição original");
        vaga.setRequisitos("Requisitos originais");
        vaga.setRemuneraValor(new BigDecimal("1000.00"));
        vaga.setFormaPagamento("Pix");
        vaga.setCidade("São Paulo");
        vaga.setEstado("SP");
        vaga.setModeloTrabalho(ModeloTrabalho.REMOTO);
        vaga.setTipoContrato("Freelance");
        vaga.setStatus(status);
        vaga.setDataPublicacao(LocalDateTime.of(2026, 1, 15, 10, 30));
        vaga.setTags(new HashSet<>());
        return vagaRepository.save(vaga);
    }

    private Tag novaTag(String nome) {
        Tag tag = new Tag();
        tag.setNome(nome);
        return tagRepository.save(tag);
    }

    private void vincularTag(Long vagaId, Long tagId) {
        jdbcTemplate.update("INSERT INTO tags_vaga (vaga_id, tag_id) VALUES (?, ?)", vagaId, tagId);
    }

    private List<Long> tagsDaVaga(Long vagaId) {
        return jdbcTemplate.queryForList(
                "SELECT tag_id FROM tags_vaga WHERE vaga_id = ? ORDER BY tag_id",
                Long.class,
                vagaId);
    }

    private Candidatura novaCandidatura(Vaga vaga, PerfilArtista artista) {
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
