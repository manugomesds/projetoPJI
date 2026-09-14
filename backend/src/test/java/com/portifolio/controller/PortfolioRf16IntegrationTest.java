package com.portifolio.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.portifolio.validation.PortfolioFixtures.*;

import com.portifolio.exception.PortfolioOperationException;
import com.portifolio.repository.*;
import com.portifolio.security.JwtService;
import com.portifolio.service.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc
class PortfolioRf16IntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScripts("db/schema-test.sql","db/catalogo-test.sql").withUrlParam("stringtype","unspecified");
    @TempDir static Path root;
    @DynamicPropertySource static void propriedades(DynamicPropertyRegistry r) { r.add("app.portfolio.storage-root",() -> root.toString()); }
    @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired UsuarioRepository usuarios; @Autowired JwtService jwt;
    @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean PortfolioArquivoRepository arquivos;
    @MockitoSpyBean PortfolioStorageService storage;
    @MockitoSpyBean PortfolioArquivoService service;
    long dono,outro,contratante; String token;
    @BeforeEach void dados() {
        dono=usuario("ARTISTA");outro=usuario("ARTISTA");contratante=usuario("CONTRATANTE");token=bearer(dono);
    }
    @AfterEach void limpar() throws Exception {
        reset(arquivos,storage,service);
        db.execute("truncate usuarios restart identity cascade");
        try(var paths=Files.walk(root)) {for(Path p:paths.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(root)).toList()) Files.deleteIfExists(p);}
    }
    @Test void uploadPersistenciaOwnerHeadersSemPath() throws Exception {
        long id=enviar("obra.png","image/png",imagem("png"));
        String ref=db.queryForObject("select url_arquivo from portfolio_arquivos where id=?",String.class,id);
        assertThat(ref).matches(dono+"/[0-9a-f-]{36}\\.png");assertThat(Files.exists(root.resolve(ref))).isTrue();
        assertThat(db.queryForObject("select artista_id from portfolio_arquivos where id=?",Long.class,id)).isEqualTo(dono);
        mvc.perform(get("/api/portfolio/me/arquivos").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].contentUrl").value("/api/portfolio/arquivos/"+id+"/conteudo"))
                .andExpect(jsonPath("$.content[0].urlArquivo").doesNotExist()).andExpect(jsonPath("$.content[0].artista").doesNotExist());
        mvc.perform(get("/api/portfolio/arquivos/"+id+"/conteudo").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(content().bytes(imagem("png")))
                .andExpect(header().string("Content-Type","image/png")).andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(header().string("Content-Length",""+imagem("png").length)).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(delete("/api/portfolio/arquivos/"+id).header("Authorization",token)).andExpect(status().isNoContent());
        assertThat(Files.exists(root.resolve(ref))).isFalse();assertThat(arquivos.count()).isZero();
    }
    @Test void autenticaEPreservaPropriedade() throws Exception {
        var file=new MockMultipartFile("arquivo","a.pdf","application/pdf",pdf());
        mvc.perform(multipart("/api/portfolio/arquivos").file(file)).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/portfolio/arquivos").file(file).header("Authorization",bearer(contratante))).andExpect(status().isForbidden());
        long id=enviar("a.pdf","application/pdf",pdf());
        for(String auth:new String[]{bearer(outro),token}) {
            long alvo=auth.equals(token) ? 999999L : id;
            mvc.perform(delete("/api/portfolio/arquivos/"+alvo).header("Authorization",auth)).andExpect(status().isNotFound());
            mvc.perform(get("/api/portfolio/arquivos/"+alvo+"/conteudo").header("Authorization",auth)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/portfolio/arquivos/"+id+"/conteudo")).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @ValueSource(strings={"BLOQUEADA","PENDENTE_CONSENTIMENTO","PENDENTE_TIPO_PERFIL","PENDENTE_VERIFICACAO_EMAIL"})
    void estadoContaImpedeGestao(String estado) throws Exception {
        long id=enviar("a.pdf","application/pdf",pdf());db.update("update usuarios set status_conta=? where id=?",estado,dono);
        mvc.perform(multipart("/api/portfolio/arquivos").file(new MockMultipartFile("arquivo","a.pdf","application/pdf",pdf())).header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/portfolio/me/arquivos").header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/portfolio/arquivos/"+id).header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/portfolio/publico/arquivos/"+id+"/conteudo")).andExpect(status().isNotFound());
    }
    @Test void adultoPublicoPdfAttachmentERangeMp3() throws Exception {
        long id=enviar("a.pdf","application/pdf",pdf());
        mvc.perform(get("/api/portfolio/publico/artistas/"+dono+"/arquivos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].contentUrl").value("/api/portfolio/publico/arquivos/"+id+"/conteudo"));
        mvc.perform(get("/api/portfolio/publico/arquivos/"+id+"/conteudo")).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(header().string("Content-Type","application/pdf"));
        long audio=enviar("a.mp3","audio/mpeg",mp3());
        mvc.perform(get("/api/portfolio/publico/arquivos/"+audio+"/conteudo").header("Range","bytes=0-3"))
                .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range","bytes 0-3/834"))
                .andExpect(content().bytes(Arrays.copyOf(mp3(),4)));
        mvc.perform(get("/api/portfolio/publico/arquivos/"+audio+"/conteudo").header("Range","bytes=900-1000")).andExpect(status().isRequestedRangeNotSatisfiable());
    }
    @Test void menorAtivoComConsentimentoSomentePrivado() throws Exception {
        db.update("update usuarios set data_nascimento=? where id=?",LocalDate.now().minusYears(17),dono);
        db.update("insert into responsaveis_legais(usuario_id,nome_responsavel,telefone_responsavel,email_responsavel,versao_termo,data_consentimento,consentimento_revogado) values (?,'Responsável','11999999999','privado@test','v1',current_timestamp,false)",dono);
        long id=enviar("a.pdf","application/pdf",pdf());
        mvc.perform(get("/api/portfolio/arquivos/"+id+"/conteudo").header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(get("/api/portfolio/publico/artistas/"+dono+"/arquivos")).andExpect(status().isNotFound());
        mvc.perform(get("/api/portfolio/publico/artistas/"+dono+"/videos")).andExpect(status().isNotFound());
        mvc.perform(get("/api/portfolio/publico/arquivos/"+id+"/conteudo")).andExpect(status().isNotFound());
        mvc.perform(get("/api/portfolio/publico/arquivos/"+id+"/conteudo").header("Range","bytes=0-3")).andExpect(status().isNotFound());
    }
    @Test void paginacaoEstavel20Max50() throws Exception {
        long max=0;
        for(int i=0;i<51;i++) max=db.queryForObject("insert into portfolio_arquivos(artista_id,url_arquivo,nome_original,tamanho_bytes,tipo_mime,data_upload) values (?,'referencia-nao-lida','a.pdf',5,'application/pdf','2026-01-01') returning id",Long.class,dono);
        mvc.perform(get("/api/portfolio/me/arquivos").header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.content[0].id").value(max)).andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(get("/api/portfolio/me/arquivos").param("page","1").param("size","50").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.hasMore").value(false));
        for(String size:List.of("0","51","-1")) mvc.perform(get("/api/portfolio/me/arquivos").param("size",size).header("Authorization",token)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/portfolio/me/arquivos").param("page","-1").header("Authorization",token)).andExpect(status().isUnprocessableEntity());
    }
    @Test void falhaPersistenciaLimpaArquivo() throws Exception {
        doThrow(new DataIntegrityViolationException("segredo SQL/path")).when(arquivos).saveAndFlush(any());
        erroUpload();assertThat(arquivos.count()).isZero();assertThat(numeroArquivos()).isZero();
    }
    @Test void falhaStorageParcialLimpaArquivo() throws Exception {
        doAnswer(i -> {i.callRealMethod();throw new PortfolioOperationException(new IOException("disco"));}).when(storage).gravar(anyLong(),anyString(),any());
        erroUpload();assertThat(arquivos.count()).isZero();assertThat(numeroArquivos()).isZero();
    }
    @Test void falhaAntesDoCommitLimpaArquivo() throws Exception {
        doAnswer(i -> {Object result=i.callRealMethod();TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void beforeCommit(boolean readOnly){throw new IllegalStateException("commit falhou");}
        });return result;}).when(arquivos).saveAndFlush(any());
        erroUpload();assertThat(arquivos.count()).isZero();assertThat(numeroArquivos()).isZero();
    }
    @Test void rollbackExternoLimpaUpload() {
        new TransactionTemplate(manager).execute(status -> {
            try {enviar("a.pdf","application/pdf",pdf());}catch(Exception e){throw new RuntimeException(e);}
            status.setRollbackOnly();return null;
        });assertThat(arquivos.count()).isZero();assertThat(numeroArquivos()).isZero();
    }
    @Test void rollbackRemocaoRestauraConteudo() throws Exception {
        long id=enviar("a.pdf","application/pdf",pdf());
        new TransactionTemplate(manager).execute(status -> {
            try {mvc.perform(delete("/api/portfolio/arquivos/"+id).header("Authorization",token)).andExpect(status().isNoContent());}
            catch(Exception e){throw new RuntimeException(e);}status.setRollbackOnly();return null;
        });assertThat(arquivos.existsById(id)).isTrue();assertThat(numeroArquivos()).isEqualTo(1);
        mvc.perform(get("/api/portfolio/arquivos/"+id+"/conteudo").header("Authorization",token)).andExpect(content().bytes(pdf()));
    }
    @Test void falhaRemocaoMantemRegistroEArquivo() throws Exception {
        long id=enviar("a.pdf","application/pdf",pdf());
        doThrow(new PortfolioOperationException(new IOException("falha privada"))).when(storage).remover(anyLong(),anyString());
        mvc.perform(delete("/api/portfolio/arquivos/"+id).header("Authorization",token)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensagem").value("Não foi possível processar o portfólio. Tente novamente."));
        assertThat(arquivos.existsById(id)).isTrue();assertThat(numeroArquivos()).isEqualTo(1);
    }
    @Test void pathPersistidoInseguroNuncaLidoOuExcluido() throws Exception {
        long id=enviar("a.pdf","application/pdf",pdf());db.update("update portfolio_arquivos set url_arquivo='../fora.pdf' where id=?",id);
        mvc.perform(get("/api/portfolio/arquivos/"+id+"/conteudo").header("Authorization",token)).andExpect(status().isInternalServerError());
        mvc.perform(delete("/api/portfolio/arquivos/"+id).header("Authorization",token)).andExpect(status().isInternalServerError());
        assertThat(arquivos.existsById(id)).isTrue();assertThat(numeroArquivos()).isEqualTo(1);
    }
    @Test void limitesHttp413EValidacao422() throws Exception {
        mvc.perform(multipart("/api/portfolio/arquivos").file(new MockMultipartFile("arquivo","a.jpg","image/jpeg",new byte[]{'M','Z'})).header("Authorization",token))
                .andExpect(status().isUnprocessableEntity());
        doThrow(new org.springframework.web.multipart.MaxUploadSizeExceededException(22L*1024*1024)).when(service).enviar(any());
        mvc.perform(multipart("/api/portfolio/arquivos").file(new MockMultipartFile("arquivo","a.pdf","application/pdf",pdf())).header("Authorization",token))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.status").value(413));
    }
    @Test void videosCadastroListagemHtmlNaoConfiavelPropriedade() throws Exception {
        String base="/api/portfolio/videos";
        mvc.perform(post(base).header("Authorization",token).contentType("application/json").content("{\"url\":\"https://youtu.be/dQw4w9WgXcQ\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.embedUrl").value("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"));
        long id=db.queryForObject("select id from embeds_externos",Long.class);
        db.update("update embeds_externos set codigo_iframe='<script>evil()</script>' where id=?",id);
        mvc.perform(get("/api/portfolio/publico/artistas/"+dono+"/videos")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].codigoIframe").doesNotExist()).andExpect(jsonPath("$.size").value(20));
        mvc.perform(delete(base+"/"+id).header("Authorization",bearer(outro))).andExpect(status().isNotFound());
        mvc.perform(post(base).header("Authorization",token).contentType("application/json").content("{\"url\":\"https://vimeo.com/123\",\"iframe\":\"<iframe>\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(delete(base+"/"+id).header("Authorization",token)).andExpect(status().isNoContent());
        assertThat(db.queryForObject("select count(*) from embeds_externos",Long.class)).isZero();
    }
    long enviar(String name,String mime,byte[] bytes) throws Exception {
        var r=mvc.perform(multipart("/api/portfolio/arquivos").file(new MockMultipartFile("arquivo",name,mime,bytes))
                .param("artistaId",""+outro).header("Authorization",token)).andExpect(status().isCreated()).andReturn();
        return ((Number)com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(),"$.id")).longValue();
    }
    void erroUpload() throws Exception {
        mvc.perform(multipart("/api/portfolio/arquivos").file(new MockMultipartFile("arquivo","a.pdf","application/pdf",pdf())).header("Authorization",token))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.mensagem").value("Não foi possível processar o portfólio. Tente novamente."));
    }
    long numeroArquivos() {try(var paths=Files.walk(root)){return paths.filter(Files::isRegularFile).count();}catch(IOException e){throw new java.io.UncheckedIOException(e);}}
    String bearer(long id){return "Bearer "+jwt.gerarToken(usuarios.findById(id).orElseThrow());}
    long usuario(String tipo) {
        long id=db.queryForObject("insert into usuarios(nome,data_nascimento,telefone,email,senha,tipo_usuario,status_conta,perfil_completo) values ('Artista','1990-01-01','11999999999',?,'hash',?,'ATIVA',false) returning id",Long.class,UUID.randomUUID()+"@rf16.test",tipo);
        if(tipo.equals("ARTISTA")) db.update("insert into perfis_artistas(usuario_id,tipo_perfil_artistico) values (?,'ARTISTA_SOLO')",id);
        return id;
    }
}
