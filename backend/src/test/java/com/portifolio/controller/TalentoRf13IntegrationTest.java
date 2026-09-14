package com.portifolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

import com.portifolio.repository.UsuarioRepository;
import com.portifolio.security.JwtService;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc
class TalentoRf13IntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScripts("db/schema-test.sql", "db/catalogo-test.sql")
            .withUrlParam("stringtype", "unspecified");
    @Autowired MockMvc mvc;
    @MockitoSpyBean JdbcTemplate db;
    @Autowired UsuarioRepository usuarios;
    @Autowired JwtService jwt;
    long dono, outro, f1, f2, fOutra, e1, e2, vaga;
    String token;

    @BeforeEach void dados() {
        dono = usuario("CONTRATANTE", "ATIVA", true);
        outro = usuario("CONTRATANTE", "ATIVA", true);
        db.update("insert into perfis_contratantes(usuario_id) values (?),(?)", dono, outro);
        f1 = funcao(1, "Voz"); f2 = funcao(1, "Instrumento"); fOutra = funcao(2, "Desenho");
        e1 = especializacao("Jazz", f1); e2 = especializacao("Popular", f2);
        vaga = vaga(dono, 1, f1, f2);
        db.update("insert into vaga_especializacao values (?,?),(?,?)", vaga, e1, vaga, e2);
        token = bearer(dono);
    }
    @AfterEach void limpar() {
        db.execute("truncate usuarios, funcoes, especializacoes restart identity cascade");
    }

    @Test void autenticacaoAutorizacaoEIdentidade() throws Exception {
        mvc.perform(get("/api/talentos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/talentos").header("Authorization","Bearer invalido")).andExpect(status().isUnauthorized());
        long artista = artista(1, true, f1);
        mvc.perform(get("/api/talentos").header("Authorization",bearer(artista))).andExpect(status().isForbidden());
        mvc.perform(req().param("contratanteId", ""+outro).param("usuarioId", ""+outro).param("recomendados","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contexto.id").value(vaga));
        mvc.perform(get("/api/usuarios").header("Authorization",token)).andExpect(status().isForbidden());
    }
    @Test void propriedadeDeContexto() throws Exception {
        long alheia = vaga(outro, 1, f1);
        mvc.perform(req().param("vagaId",""+alheia)).andExpect(status().isNotFound());
        mvc.perform(req().param("vagaId","999999")).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"PENDENTE_VERIFICACAO_EMAIL","PENDENTE_TIPO_PERFIL","PENDENTE_CONSENTIMENTO","BLOQUEADA"})
    void somenteEstadoAtivo(String estado) throws Exception {
        long id=artista(1,true,f1);
        db.update("update usuarios set status_conta=? where id=?",estado,id);
        mvc.perform(req()).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void somenteArtistaCompleto() throws Exception {
        long id=artista(1,true,f1);
        artista(1,false,f1);
        long impostor=artista(1,true,f1);
        db.update("update usuarios set tipo_usuario='CONTRATANTE' where id=?",impostor);
        mvc.perform(req()).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].artistaId").value(id));
    }
    @Test void contratanteBloqueadoNaoAcessa() throws Exception {
        db.update("update usuarios set status_conta='BLOQUEADA' where id=?",dono);
        mvc.perform(req()).andExpect(status().isForbidden());
    }
    @Test void menoresMesmoAtivosComConsentimentoNaoTemAutorizacaoDeExposicao() throws Exception {
        long id=artista(1,true,f1);
        db.update("update usuarios set data_nascimento=? where id=?",LocalDate.now().minusYears(17),id);
        db.update("""
                insert into responsaveis_legais(usuario_id,nome_responsavel,telefone_responsavel,email_responsavel,
                   versao_termo,consentimento_revogado,data_consentimento) values (?,'Privado','11999999999','privado@test','v1',false,current_timestamp)
                """,id);
        mvc.perform(req()).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/perfis/publicos/ARTISTA/"+id)).andExpect(status().isNotFound());
    }
    @Test void adultoNoAniversarioDeDezoitoElegivel() throws Exception {
        long id=artista(1,true,f1);
        db.update("update usuarios set data_nascimento=? where id=?",LocalDate.now().minusYears(18),id);
        mvc.perform(req()).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void idadeUsaDataDaAplicacaoMesmoComFusoDiferenteNoPostgres() throws Exception {
        db.execute("set local time zone 'Pacific/Kiritimati'");
        LocalDate hoje=LocalDate.now();
        LocalDate dataBanco=db.queryForObject("select current_date",LocalDate.class);
        if (dataBanco.equals(hoje)) {
            db.execute("set local time zone 'Etc/GMT+12'");
            dataBanco=db.queryForObject("select current_date",LocalDate.class);
        }
        assertThat(dataBanco).isNotEqualTo(hoje);
        long adulto=artista(1,true,f1),adolescente=artista(1,true,f1);
        db.update("update usuarios set data_nascimento=? where id=?",hoje.minusYears(18),adulto);
        db.update("update usuarios set data_nascimento=? where id=?",hoje.plusDays(1).minusYears(18),adolescente);
        mvc.perform(req()).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].artistaId").value(adulto));
    }
    @Test void areaPrincipalESecundariaMesmoPesoEAreaDaVagaObrigatoria() throws Exception {
        long a=artista(1,true,f1);
        long b=artista(2,true,fOutra);
        area(b,1,false,"INICIANTE",f1);
        artista(2,true,fOutra);
        mvc.perform(req().param("vagaId",""+vaga)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].artistaId").value(a))
                .andExpect(jsonPath("$.content[1].artistaId").value(b))
                .andExpect(jsonPath("$.content[0].quantidadeFuncoesCoincidentes").value(1))
                .andExpect(jsonPath("$.content[1].quantidadeFuncoesCoincidentes").value(1));
    }
    @Test void matchingHierarquicoAtualizacaoEId() throws Exception {
        long um=artista(1,true,f1);
        long doisSemSpec=artista(1,true,f1,f2);
        long doisSpecAntigo=artista(1,true,f1,f2); spec(doisSpecAntigo,1,e1);
        long doisSpecNovo=artista(1,true,f1,f2); spec(doisSpecNovo,1,e1);
        long empateId=artista(1,true,f1,f2); spec(empateId,1,e1);
        db.update("update perfis_artistas set ultima_atualizacao='2026-01-02' where usuario_id in (?,?)",doisSpecNovo,empateId);
        mvc.perform(req().param("vagaId",""+vaga)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].artistaId").value(org.hamcrest.Matchers.contains(
                        (int)doisSpecNovo,(int)empateId,(int)doisSpecAntigo,(int)doisSemSpec,(int)um)))
                .andExpect(jsonPath("$.content[0].quantidadeFuncoesCoincidentes").value(2))
                .andExpect(jsonPath("$.content[0].quantidadeEspecializacoesCoincidentes").value(1));
    }
    @Test void ausenciaDeFuncoesEEspecializacoesNaVagaNaoExcluiArtista() throws Exception {
        long sem=vaga(dono,1);
        artista(1,true);
        artista(1,true,f1);
        mvc.perform(req().param("vagaId",""+sem)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].quantidadeFuncoesCoincidentes").value(0))
                .andExpect(jsonPath("$.content[1].quantidadeEspecializacoesCoincidentes").value(0));
    }
    @Test void vagaSemEspecializacoesUsaFuncoes() throws Exception {
        long sem=vaga(dono,1,f1);
        artista(1,true,f1);
        mvc.perform(req().param("vagaId",""+sem)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].quantidadeFuncoesCoincidentes").value(1))
                .andExpect(jsonPath("$.content[0].quantidadeEspecializacoesCoincidentes").value(0));
    }
    @ParameterizedTest @CsvSource({"SEM_EXPERIENCIA,6","INICIANTE,4","INTERMEDIARIO,3","EXPERIENTE,2","ESPECIALISTA,1"})
    void experienciaMinimaOrdinal(String minimo,int total) throws Exception {
        for(String nivel:List.of("SEM_EXPERIENCIA","INICIANTE","INTERMEDIARIO","EXPERIENTE","ESPECIALISTA")) {
            long id=artista(1,true,f1);
            db.update("update perfil_artista_area set nivel_experiencia=? where perfil_artista_id=?",nivel,id);
        }
        long sem=artista(1,true,f1);
        db.update("update perfil_artista_area set nivel_experiencia=null where perfil_artista_id=?",sem);
        mvc.perform(req().param("areaId","1").param("experienciaMinima",minimo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(total));
    }
    @Test void experienciaDeOutraAreaNaoSatisfazFiltro() throws Exception {
        long id=artista(1,true,f1);
        area(id,2,false,"ESPECIALISTA",fOutra);
        mvc.perform(req().param("areaId","1").param("experienciaMinima","ESPECIALISTA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(req().param("experienciaMinima","EXPERIENTE")).andExpect(status().isUnprocessableEntity());
    }
    @ParameterizedTest @CsvSource({"true,1","false,1"})
    void disponibilidadeExataSemConverterNull(String valor,int total) throws Exception {
        long a=artista(1,true,f1),b=artista(1,true,f1); artista(1,true,f1);
        db.update("update perfis_artistas set disponivel_oportunidades=true where usuario_id=?",a);
        db.update("update perfis_artistas set disponivel_oportunidades=false where usuario_id=?",b);
        mvc.perform(req().param("disponivel",valor)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(total));
        mvc.perform(req()).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
    }
    @Test void filtrosOrDentroEAndEntreTipos() throws Exception {
        long a=artista(1,true,f1),b=artista(1,true,f2),c=artista(1,true,f1);
        db.update("update perfis_artistas set tipo_perfil_artistico='BANDA',raio_atuacao='REMOTO',disponivel_oportunidades=true where usuario_id=?",a);
        db.update("update perfis_artistas set tipo_perfil_artistico='GRUPO_ARTISTICO',raio_atuacao='NACIONAL',disponivel_oportunidades=true where usuario_id=?",b);
        db.update("update perfis_artistas set tipo_perfil_artistico='BANDA',raio_atuacao='LOCAL',disponivel_oportunidades=true where usuario_id=?",c);
        mvc.perform(req().param("areaId","1").param("funcaoIds",f1+","+f2).param("tipos","BANDA,GRUPO_ARTISTICO")
                .param("raios","REMOTO,NACIONAL").param("disponivel","true").param("localizacao","recife"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }
    @Test void especializacoesOrECompatibilidadeComFuncaoDoArtista() throws Exception {
        long a=artista(1,true,f1),b=artista(1,true,f2); spec(a,1,e1);spec(b,1,e2);
        long invalido=artista(1,true,f1);spec(invalido,1,e2);
        mvc.perform(req().param("areaId","1").param("funcaoIds",f1+","+f2).param("especializacaoIds",e1+","+e2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }
    @Test void taxonomiaInvalidaRejeitada() throws Exception {
        mvc.perform(req().param("areaId","999")).andExpect(status().isUnprocessableEntity());
        mvc.perform(req().param("areaId","1").param("funcaoIds",""+fOutra)).andExpect(status().isUnprocessableEntity());
        mvc.perform(req().param("areaId","1").param("funcaoIds","999999")).andExpect(status().isUnprocessableEntity());
        mvc.perform(req().param("funcaoIds",""+f1)).andExpect(status().isUnprocessableEntity());
        mvc.perform(req().param("areaId","1").param("funcaoIds",""+f1).param("especializacaoIds",""+e2)).andExpect(status().isUnprocessableEntity());
        mvc.perform(req().param("areaId","1").param("funcaoIds",""+f1).param("especializacaoIds","999999")).andExpect(status().isUnprocessableEntity());
        mvc.perform(req().param("vagaId",""+vaga).param("areaId","2")).andExpect(status().isUnprocessableEntity());
    }
    @Test void localizacaoTextualLiteralNaoInjetaNemInventaCidadeEstado() throws Exception {
        artista(1,true,f1);
        mvc.perform(req().param("localizacao","%' OR 1=1 --")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(req().param("cidade","Recife")).andExpect(status().isUnprocessableEntity());
        mvc.perform(req().param("estado","PE")).andExpect(status().isUnprocessableEntity());
    }
    @ParameterizedTest @CsvSource({"size,0","size,51","page,-1","ordenacao,id desc","raios,500","disponivel,talvez","vagaId,-1"})
    void parametrosInvalidos(String nome,String valor) throws Exception {
        mvc.perform(req().param(nome,valor)).andExpect(status().isBadRequest());
    }
    @Test void paginaVinteMaximoCinquentaEstavelSemDuplicatas() throws Exception {
        for(int i=0;i<51;i++) artista(1,true,f1);
        var first=mvc.perform(req()).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.hasMore").value(true)).andReturn().getResponse().getContentAsString();
        var next=mvc.perform(req().param("page","1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20)).andReturn().getResponse().getContentAsString();
        List<Integer> ids=com.jayway.jsonpath.JsonPath.read(first,"$.content[*].artistaId");
        List<Integer> nextIds=com.jayway.jsonpath.JsonPath.read(next,"$.content[*].artistaId");
        assertThat(nextIds).doesNotContainAnyElementsOf(ids);
        var same=mvc.perform(req()).andReturn().getResponse().getContentAsString();
        assertThat(same).isEqualTo(first);
        mvc.perform(req().param("size","50")).andExpect(jsonPath("$.content.length()").value(50)).andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(req().param("size","50").param("page","1")).andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.hasMore").value(false));
    }
    @Test void ordenarPorAtualizacaoPermitida() throws Exception {
        long a=artista(1,true,f1,f2),b=artista(1,true,f1);
        db.update("update perfis_artistas set ultima_atualizacao='2026-03-01' where usuario_id=?",b);
        mvc.perform(req().param("vagaId",""+vaga).param("ordenacao","ATUALIZACAO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].artistaId").value(b));
    }
    @Test void dtoWhitelistEPerfilPublicoFuncional() throws Exception {
        long a=artista(1,true,f1); spec(a,1,e1);
        var body=mvc.perform(req()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("telefone","email","dataNascimento","cpf","cnpj","responsavel","googleId","token","senha","enderecoCompleto","afirmativa","score","medalha");
        mvc.perform(get("/api/perfis/publicos/ARTISTA/"+a)).andExpect(status().isOk());
    }
    @Test void catalogosPaginadosCompativeisESomenteLeitura() throws Exception {
        mvc.perform(get("/api/talentos/areas").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2));
        mvc.perform(get("/api/talentos/funcoes").param("areaId","1").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2));
        mvc.perform(get("/api/talentos/especializacoes").param("areaId","1").param("funcaoIds",""+f1).header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(e1)).andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(post("/api/talentos/areas").header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/talentos/areas")).andExpect(status().isUnauthorized());
    }
    @Test void recomendacoesUsamContextoProprioMaisRecenteEReutilizamDashboard() throws Exception {
        artista(1,true,f1);
        long recente=vaga(dono,1,f1);
        vaga(outro,2,fOutra);
        mvc.perform(req().param("recomendados","true")).andExpect(status().isOk()).andExpect(jsonPath("$.contexto.id").value(recente));
        mvc.perform(get("/api/dashboard").header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.talentosSugeridos.content[0].quantidadeFuncoesCoincidentes").value(1));
        mvc.perform(get("/api/talentos/contextos").header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        db.update("update vagas set status='ENCERRADA' where contratante_id=?",dono);
        mvc.perform(req().param("recomendados","true")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void consultasConstantesEntreUmECinquentaPerfis() throws Exception {
        for(int i=0;i<50;i++) { long a=artista(1,true,f1,f2);spec(a,1,e1); }
        clearInvocations(db);
        mvc.perform(req().param("size","1").param("vagaId",""+vaga)).andExpect(status().isOk());
        long uma=quantidadeConsultas();
        clearInvocations(db);
        mvc.perform(req().param("size","50").param("vagaId",""+vaga)).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(50));
        assertThat(quantidadeConsultas()).isEqualTo(uma).isEqualTo(6);
        System.out.println("RF13 JDBC consultas por página (1/50 perfis): "+uma+"/"+quantidadeConsultas());
        explicarConsultaExecutada();
    }

    private void explicarConsultaExecutada() {
        // Reaproveita o PreparedStatementCreator real: mesmo SQL e parâmetros do endpoint.
        var consulta = (org.springframework.jdbc.core.PreparedStatementCreator) mockingDetails(db).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("query") && i.getArguments().length == 3
                        && i.getArguments()[0] instanceof org.springframework.jdbc.core.SqlProvider p
                        && p.getSql().contains("quantidade_funcoes"))
                .findFirst().orElseThrow().getArguments()[0];
        db.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            var explain = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                    java.sql.Connection.class.getClassLoader(), new Class<?>[]{java.sql.Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement") && args[0] instanceof String sql)
                            args[0] = "explain (analyze, buffers) " + sql;
                        try { return method.invoke(connection, args); }
                        catch (java.lang.reflect.InvocationTargetException ex) { throw ex.getCause(); }
                    });
            StringBuilder plan = new StringBuilder("RF13 EXPLAIN ANALYZE — 50 perfis, matching completo\n");
            try (var statement = consulta.createPreparedStatement(explain); var rows = statement.executeQuery()) {
                while (rows.next()) plan.append(rows.getString(1)).append('\n');
            }
            assertThat(plan.toString()).contains("Execution Time:", "Limit", "Sort");
            System.out.println(plan);
            return null;
        });
    }

    long quantidadeConsultas() {
        // Conta a execução central do JdbcTemplate, sem contar wrappers/overloads como SQL extra.
        return mockingDetails(db).getInvocations().stream().filter(i -> i.getMethod().getName().equals("query")
                && i.getArguments().length == 3 && i.getArguments()[0] instanceof org.springframework.jdbc.core.PreparedStatementCreator).count();
    }
    MockHttpServletRequestBuilder req() { return get("/api/talentos").header("Authorization",token); }
    String bearer(long id) { return "Bearer "+jwt.gerarToken(usuarios.findById(id).orElseThrow()); }
    long usuario(String tipo,String estado,boolean completo) {
        return db.queryForObject("""
                insert into usuarios(nome,data_nascimento,telefone,email,senha,tipo_usuario,status_conta,perfil_completo)
                values ('Artista profissional','1990-01-01','11999999999',?,'hash-teste',?,?,?) returning id
                """,Long.class,UUID.randomUUID()+"@rf13.test",tipo,estado,completo);
    }
    long artista(int area,boolean completo,long...funcoes) {
        long id=usuario("ARTISTA","ATIVA",completo);
        db.update("""
                insert into perfis_artistas(usuario_id,biografia,localizacao,url_portfolio,tipo_perfil_artistico,raio_atuacao,ultima_atualizacao)
                values (?,'Biografia profissional','Recife - PE','https://example.test/portfolio','ARTISTA_SOLO','LOCAL','2026-01-01')
                """,id);
        area(id,area,true,"INICIANTE",funcoes); return id;
    }
    void area(long id,int area,boolean principal,String nivel,long...funcs) {
        db.update("insert into perfil_artista_area(perfil_artista_id,area_id,principal,nivel_experiencia) values (?,?,?,?)",id,area,principal,nivel);
        for(long f:funcs) db.update("insert into perfil_artista_funcao values (?,?,?)",id,area,f);
    }
    void spec(long id,int area,long e) { db.update("insert into perfil_artista_especializacao values (?,?,?)",id,area,e); }
    long funcao(int area,String nome) { return db.queryForObject("insert into funcoes(area_id,nome) values (?,?) returning id",Long.class,area,nome); }
    long especializacao(String nome,long f) {
        long id=db.queryForObject("insert into especializacoes(nome) values (?) returning id",Long.class,nome);
        db.update("insert into funcao_especializacao values (?,?)",f,id); return id;
    }
    long vaga(long dono,int area,long...funcs) {
        long id=db.queryForObject("""
                insert into vagas(contratante_id,area_id,titulo,descricao,requisitos,cidade,estado,tipo_contrato,abrangencia,status,data_publicacao)
                values (?,?,'Contexto profissional','Descrição','Requisitos','Recife','PE','Evento','LOCAL','ABERTA','2026-01-01') returning id
                """,Long.class,dono,area);
        for(long f:funcs) db.update("insert into vaga_funcao values (?,?)",id,f);return id;
    }
}
