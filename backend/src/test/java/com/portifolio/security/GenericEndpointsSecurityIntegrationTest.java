package com.portifolio.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portifolio.model.enums.TipoUsuario;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class GenericEndpointsSecurityIntegrationTest {

    private static final String SENHA = "PalcoTeste123!";
    private static final List<String> TABELAS = List.of(
            "usuarios", "perfis_artistas", "perfis_contratantes", "tags", "tags_artista",
            "vagas", "tags_vaga", "candidaturas", "salas_chat", "participantes_chat",
            "mensagens_chat", "notificacoes", "refresh_tokens", "log_vagas_canceladas");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScript("db/schema-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();
    private Conta artista;
    private Conta contratante;
    private Conta menor;
    private Long tagId;
    private Long vagaId;

    @BeforeEach
    void prepararHistoricoRelacionado() throws Exception {
        artista = cadastrar("artista", "ARTISTA", false);
        contratante = cadastrar("contratante", "CONTRATANTE", false);
        menor = cadastrar("menor", "ARTISTA", true);
        tagId = jdbc.queryForObject("insert into tags(nome) values ('Teste segurança') returning id", Long.class);
        jdbc.update("insert into tags_artista(artista_id, tag_id) values (?, ?)", artista.id(), tagId);
        jdbc.update("update perfis_artistas set biografia = ?, localizacao = ?, url_portfolio = ? where usuario_id = ?",
                "Biografia privada do menor", "Local privado", "https://example.test/privado", menor.id());
        vagaId = jdbc.queryForObject("""
                insert into vagas(contratante_id,titulo,descricao,requisitos,remunera_valor,
                    forma_pagamento,cidade,estado,modelo_trabalho,tipo_contrato)
                values (?, 'Vaga de teste', 'Descrição', 'Requisitos', 100, 'Pix', 'São Paulo', 'SP', 'remoto', 'Freelance') returning id
                """, Long.class, contratante.id());
        jdbc.update("insert into tags_vaga(vaga_id,tag_id) values (?,?)", vagaId, tagId);
        jdbc.update("insert into candidaturas(vaga_id,artista_id,mensagem_apresentacao,link_portfolio_candidatura) values (?,?,?,?)",
                vagaId, artista.id(), "Apresentação", "https://example.test/portfolio");
        Long salaId = jdbc.queryForObject("insert into salas_chat default values returning id", Long.class);
        jdbc.update("insert into participantes_chat(sala_id,usuario_id) values (?,?),(?,?)",
                salaId, artista.id(), salaId, contratante.id());
        jdbc.update("insert into mensagens_chat(sala_id,remetente_id,texto_mensagem) values (?,?,?)",
                salaId, artista.id(), "Histórico que não pode desaparecer");
        jdbc.update("insert into notificacoes(usuario_destino_id,tipo_notificacao,mensagem_alerta,link_contexto) values (?,'mensagem',?,?)",
                contratante.id(), "Alerta de teste", "/mensagens");
        jdbc.update("insert into log_vagas_canceladas(vaga_id,cancelado_por_id,motivo) values (?,?,?)",
                vagaId, contratante.id(), "Registro de teste");
    }

    @AfterEach
    void limparDadosDescartaveis() {
        jdbc.execute("TRUNCATE usuarios, tags, salas_chat RESTART IDENTITY CASCADE");
    }

    @ParameterizedTest
    @EnumSource(TipoUsuario.class)
    void autenticadoNaoCriaTerceiroMesmoComSenhaValida(TipoUsuario papel) throws Exception {
        Map<String, List<String>> antes = snapshot();
        Conta solicitante = papel == TipoUsuario.ARTISTA ? artista : contratante;
        mvc.perform(post("/api/usuarios").header("Authorization", solicitante.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dadosCadastro("terceiro", papel.name(), false))))
                .andExpect(status().isForbidden());
        assertThat(snapshot()).isEqualTo(antes);
    }

    @Test
    void cadastroOficialLoginEEdicaoPropriaContinuamFuncionais() throws Exception {
        Conta nova = cadastrar("oficial", "ARTISTA", false);
        mvc.perform(get("/api/usuarios/me").header("Authorization", nova.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(nova.id()));
        mvc.perform(put("/api/usuarios/me").header("Authorization", nova.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                                "nome", "Nome atualizado", "email", "oficial@security-api.test", "telefone", "11999999999"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Nome atualizado"));
        mvc.perform(get("/api/usuarios/{id}", nova.id()).header("Authorization", nova.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("oficial@security-api.test"));
    }

    @Test
    void anonimoETokenInvalidoNaoAcessamOperacoesContidas() throws Exception {
        for (String token : List.of("", "Bearer token-invalido")) {
            for (String path : List.of("/api/usuarios", "/api/tags")) {
                mvc.perform(post(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isUnauthorized());
            }
            for (String path : caminhosDelete()) {
                mvc.perform(delete(path).header("Authorization", token)).andExpect(status().isUnauthorized());
            }
            for (String path : List.of("/api/perfis-artistas", "/api/perfis-contratantes", "/api/usuarios",
                    "/api/perfis-artistas/" + menor.id(), "/api/perfis-contratantes/" + contratante.id(), "/api/tags")) {
                mvc.perform(get(path).header("Authorization", token)).andExpect(status().isUnauthorized());
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"POST, /api/tags", "PUT, /api/tags/1", "PATCH, /api/tags/1", "DELETE, /api/tags/1",
            "POST, /api/tags/", "POST, /api/usuarios/", "DELETE, /api/tags"})
    void variantesDeMutacaoSaoBloqueadasAntesDoBody(String method, String path) throws Exception {
        Map<String, List<String>> antes = snapshot();
        for (Conta conta : List.of(artista, contratante)) {
            mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", conta.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Catálogo adulterado\"}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(snapshot()).isEqualTo(antes);
    }

    @Test
    void leituraDeTagsEAssociacoesLegitimasContinuamFuncionais() throws Exception {
        mvc.perform(get("/api/tags").header("Authorization", artista.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(tagId));
        mvc.perform(get("/api/tags/{id}", tagId).header("Authorization", contratante.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(tagId));
        mvc.perform(put("/api/perfis-artistas/{id}", artista.id()).header("Authorization", artista.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                                "usuarioId", artista.id(), "biografia", "Arte", "localizacao", "São Paulo",
                                "urlPortfolio", "https://example.test/artista", "tagIds", List.of(tagId)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tagIds[0]").value(tagId));
        Map<String, Object> vaga = new LinkedHashMap<>(Map.of(
                "titulo", "Nova vaga", "descricao", "Descrição", "requisitos", "Requisitos", "remuneraValor", 200,
                "formaPagamento", "Pix", "cidade", "São Paulo", "estado", "SP", "modeloTrabalho", "REMOTO",
                "tipoContrato", "Freelance", "tagIds", List.of(tagId)));
        JsonNode criada = mapper.readTree(mvc.perform(post("/api/vagas").header("Authorization", contratante.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(vaga)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tagIds[0]").value(tagId))
                .andReturn().getResponse().getContentAsString());
        vaga.put("titulo", "Vaga editada");
        mvc.perform(put("/api/vagas/{id}", criada.get("id").asLong()).header("Authorization", contratante.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(vaga)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tagIds[0]").value(tagId));
        assertThat(jdbc.queryForObject("select count(*) from tags", Integer.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"ARTISTA,false", "ARTISTA,true", "CONTRATANTE,false", "CONTRATANTE,true"})
    void perfilPrivadoSoDoTitularERf10PermaneceSeguro(String tipo, boolean ehMenor) throws Exception {
        Conta titular = cadastrar("titular", tipo, ehMenor);
        String path = (tipo.equals("ARTISTA") ? "/api/perfis-artistas/" : "/api/perfis-contratantes/") + titular.id();
        mvc.perform(get(path).header("Authorization", titular.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuarioId").value(titular.id()));
        for (Conta terceiro : List.of(artista, contratante)) {
            String body = mvc.perform(get(path).header("Authorization", terceiro.bearer()))
                    .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("titular@security-api.test", "telefoneResponsavel", "urlPortfolio", "avatarUrl");
            mvc.perform(get("/api/usuarios/{id}", titular.id()).header("Authorization", terceiro.bearer()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/perfis/publicos/{tipo}/{id}", tipo, titular.id()))
                .andExpect(ehMenor ? status().isNotFound() : status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.dataNascimento").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/usuarios", "/api/perfis-artistas", "/api/perfis-contratantes"})
    void colecoesPrivadasNaoSaoRotaAlternativa(String path) throws Exception {
        for (Conta conta : List.of(artista, contratante, menor)) {
            mvc.perform(get(path).param("usuarioId", conta.id().toString()).header("Authorization", conta.bearer()))
                    .andExpect(status().isForbidden());
            mvc.perform(head(path).header("Authorization", conta.bearer())).andExpect(status().isForbidden());
        }
    }

    @Test
    void idsAlternativosNaoSubstituemTitular() throws Exception {
        for (String id : List.of(menor.id().toString(), "0" + menor.id(), "999999")) {
            mvc.perform(get("/api/perfis-artistas/" + id).header("Authorization", contratante.bearer())
                            .param("usuarioId", contratante.id().toString()))
                    .andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void hardDeletesPropriosEAlheiosPreservamTodoHistorico(int indice) throws Exception {
        Map<String, List<String>> antes = snapshot();
        for (Conta conta : List.of(artista, contratante)) {
            mvc.perform(delete(caminhosDelete().get(indice)).header("Authorization", conta.bearer())
                            .param("usuarioId", conta.id().toString()))
                    .andExpect(status().isForbidden());
        }
        assertThat(snapshot()).isEqualTo(antes);
    }

    @Test
    void logoutRevogaRefreshSemExcluirContaOuHistorico() throws Exception {
        List<String> usuariosAntes = snapshot().get("usuarios");
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", artista.refreshToken()))))
                .andExpect(status().isNoContent());
        assertThat(snapshot().get("usuarios")).isEqualTo(usuariosAntes);
        assertThat(jdbc.queryForObject("select count(*) from mensagens_chat", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where usuario_id = ? and ativo = true",
                Integer.class, artista.id())).isZero();
    }

    private List<String> caminhosDelete() {
        return List.of("/api/usuarios/me", "/api/usuarios/" + artista.id(),
                "/api/perfis-artistas/" + artista.id(), "/api/perfis-contratantes/" + contratante.id());
    }

    private Map<String, List<String>> snapshot() {
        Map<String, List<String>> dados = new LinkedHashMap<>();
        for (String tabela : TABELAS) {
            // Identificadores fixos definidos exclusivamente pelo teste, nunca dados de requisição.
            dados.put(tabela, jdbc.queryForList("select to_jsonb(t)::text from " + tabela + " t order by 1", String.class));
        }
        return dados;
    }

    private Conta cadastrar(String nome, String tipo, boolean ehMenor) throws Exception {
        Map<String, Object> dados = dadosCadastro(nome, tipo, ehMenor);
        JsonNode criada = mapper.readTree(mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(json(dados))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        JsonNode sessao = mapper.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", dados.get("email"), "senha", SENHA, "rememberMe", true))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return new Conta(criada.get("id").asLong(), "Bearer " + sessao.get("token").asText(), sessao.get("refreshToken").asText());
    }

    private Map<String, Object> dadosCadastro(String nome, String tipo, boolean ehMenor) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("nome", nome);
        dados.put("email", nome + "@security-api.test");
        dados.put("dataNascimento", ehMenor ? java.time.LocalDate.now().minusYears(16).toString() : "1990-01-01");
        dados.put("telefone", "11999999999");
        dados.put("senha", SENHA);
        dados.put("tipoUsuario", tipo);
        if (ehMenor) {
            dados.put("nomeResponsavel", "Responsável sintético");
            dados.put("telefoneResponsavel", "11888888888");
            dados.put("emailResponsavel", "responsavel@security-api.test");
        }
        return dados;
    }

    private String json(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    private record Conta(Long id, String bearer, String refreshToken) { }
}
