package com.portifolio.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portifolio.model.Usuario;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.realtime.NotificacaoRealtimeGateway;
import com.portifolio.security.JwtService;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import java.util.concurrent.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers @SpringBootTest(properties="spring.jpa.properties.hibernate.generate_statistics=true") @AutoConfigureMockMvc
class SalvoRf19IntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScripts("db/schema-test.sql","db/catalogo-test.sql").withUrlParam("stringtype","unspecified");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarios;
    @Autowired JwtService jwt;
    @Autowired EntityManagerFactory emf;
    @MockitoBean NotificacaoRealtimeGateway realtime;
    ObjectMapper json=new ObjectMapper();
    Usuario a,b,artista;

    @BeforeEach void fixtures() {
        reset(realtime);
        jdbc.execute("truncate usuarios,funcoes,especializacoes restart identity cascade");
        jdbc.execute("insert into usuarios(id,nome,data_nascimento,telefone,email,senha,tipo_usuario,status_conta) values (1,'Contratante A','1990-01-01','11900000001','a@rf19.test','hash','CONTRATANTE','ATIVA'),(2,'Contratante B','1990-01-01','11900000002','b@rf19.test','hash','CONTRATANTE','ATIVA'),(3,'Marina','1990-01-01','11900000003','marina@rf19.test','hash','ARTISTA','ATIVA'),(4,'Menor privado',current_date-interval '16 years','11900000004','menor@rf19.test','hash','ARTISTA','ATIVA')");
        jdbc.execute("insert into perfis_contratantes(usuario_id,nome_empresa) values(1,'Empresa A'),(2,'Empresa B')");
        jdbc.execute("insert into perfis_artistas(usuario_id,tipo_perfil_artistico,localizacao) values(3,'ARTISTA_SOLO','São Paulo/SP'),(4,'ARTISTA_SOLO','Local privado')");
        jdbc.execute("insert into vagas(id,contratante_id,area_id,titulo,descricao,requisitos,cidade,estado,tipo_contrato,abrangencia,status,endereco_completo) values(10,2,1,'Vaga pública','Descrição','Requisitos','São Paulo','SP','Projeto','LOCAL','ABERTA','Endereço privado'),(11,2,1,'Rascunho privado','Descrição','Requisitos','São Paulo','SP','Projeto','LOCAL','RASCUNHO',null)");
        a=usuarios.findById(1L).orElseThrow(); b=usuarios.findById(2L).orElseThrow(); artista=usuarios.findById(3L).orElseThrow();
    }

    ResultActions salvar(Usuario u,String tipo,long id) throws Exception {
        return mvc.perform(post("/api/salvos").header("Authorization",token(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipoAlvo\":\""+tipo+"\",\"alvoId\":"+id+"}"));
    }
    String token(Usuario u) { return "Bearer "+jwt.gerarToken(u); }
    long count() { return jdbc.queryForObject("select count(*) from itens_salvos",Long.class); }
    ResultActions estado(Usuario u,String tipo,long id) throws Exception {
        return mvc.perform(get("/api/salvos/estado").header("Authorization",token(u)).param("tipoAlvo",tipo).param("alvoId",""+id));
    }

    @Test void todosEndpointsExigemJwt() throws Exception {
        mvc.perform(get("/api/salvos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/salvos/estado").param("tipoAlvo","VAGA").param("alvoId","10")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/salvos").contentType(MediaType.APPLICATION_JSON).content("{\"tipoAlvo\":\"VAGA\",\"alvoId\":10}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/salvos/VAGA/10")).andExpect(status().isUnauthorized());
    }
    @Test void salvaPerfilEContadorPublicoSemIdentidade() throws Exception {
        salvar(a,"PERFIL_ARTISTA",3).andExpect(status().isCreated()).andExpect(jsonPath("$.salvo").value(true)).andExpect(jsonPath("$.quantidadeSalvos").value(1));
        salvar(b,"PERFIL_ARTISTA",3).andExpect(status().isCreated());
        var res=mvc.perform(get("/api/perfis/publicos/ARTISTA/3")).andExpect(status().isOk()).andExpect(jsonPath("$.quantidadeSalvos").value(2)).andReturn();
        assertThat(res.getResponse().getContentAsString()).doesNotContain("rf19.test","cpf","cnpj","telefone","dataNascimento","responsavel","quemSalvou","usuarioIdSalvador");
    }
    @Test void salvaVagaENotificaDonoSemIdentificarAtor() throws Exception {
        salvar(a,"VAGA",10).andExpect(status().isCreated());
        salvar(a,"VAGA",10).andExpect(status().isOk());
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notificacoes where tipo_notificacao='SALVO'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select mensagem_alerta from notificacoes where tipo_notificacao='SALVO'",String.class)).isEqualTo("Sua vaga foi salva.");
        assertThat(jdbc.queryForObject("select usuario_destino_id from notificacoes where tipo_notificacao='SALVO'",Long.class)).isEqualTo(2);
    }
    @Test void perfilRepetidoEhIdempotente() throws Exception {
        salvar(a,"PERFIL_ARTISTA",3).andExpect(status().isCreated());
        salvar(a,"PERFIL_ARTISTA",3).andExpect(status().isOk());
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notificacoes where tipo_notificacao='SALVO'",Integer.class)).isEqualTo(1);
    }
    @Test void concorrenciaProduzUmaLinhaEUmEvento() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var inicio=new CountDownLatch(1);
            Callable<Integer> job=() -> { inicio.await(); return salvar(a,"PERFIL_ARTISTA",3).andReturn().getResponse().getStatus(); };
            var x=pool.submit(job);var y=pool.submit(job);inicio.countDown();
            assertThat(List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,200);
        }
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notificacoes where tipo_notificacao='SALVO'",Integer.class)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"PERFIL_ARTISTA","VAGA"})
    void alvoInexistente404(String tipo) throws Exception { salvar(a,tipo,99999).andExpect(status().isNotFound());assertThat(count()).isZero(); }
    @Test void menorNaoPodeSerDescobertoPorSalvarOuContador() throws Exception {
        salvar(a,"PERFIL_ARTISTA",4).andExpect(status().isNotFound());
        estado(a,"PERFIL_ARTISTA",4).andExpect(status().isOk()).andExpect(jsonPath("$.quantidadeSalvos").isEmpty());
        assertThat(count()).isZero();
    }
    @Test void vagaPrivadaNaoPodeSerDescoberta() throws Exception { salvar(a,"VAGA",11).andExpect(status().isNotFound());assertThat(count()).isZero(); }
    @Test void donoPodeSalvarOProprioConteudoSemNotificacao() throws Exception {
        salvar(artista,"PERFIL_ARTISTA",3).andExpect(status().isCreated());
        salvar(b,"VAGA",11).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from notificacoes",Integer.class)).isZero();
    }
    @Test void removerUsaSomenteChaveDoJwtEEhIdempotente() throws Exception {
        salvar(a,"PERFIL_ARTISTA",3).andExpect(status().isCreated());
        mvc.perform(delete("/api/salvos/PERFIL_ARTISTA/3").header("Authorization",token(b))).andExpect(status().isNoContent());
        estado(a,"PERFIL_ARTISTA",3).andExpect(jsonPath("$.salvo").value(true));
        for(int n=0;n<2;n++) mvc.perform(delete("/api/salvos/PERFIL_ARTISTA/3").header("Authorization",token(a))).andExpect(status().isNoContent());
        estado(a,"PERFIL_ARTISTA",3).andExpect(jsonPath("$.salvo").value(false)).andExpect(jsonPath("$.quantidadeSalvos").value(0));
        assertThat(count()).isZero();
    }
    @Test void usuarioDoPayloadNaoTemAutoridade() throws Exception {
        mvc.perform(post("/api/salvos").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipoAlvo\":\"VAGA\",\"alvoId\":10,\"usuarioId\":2}")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select usuario_id from itens_salvos",Long.class)).isEqualTo(1);
    }
    @Test void listaPrivadaFiltraTiposENaoExpoeCamposPrivados() throws Exception {
        jdbc.execute("insert into funcoes(id,area_id,nome) values(1,1,'Canto')");
        jdbc.execute("insert into perfil_artista_area(perfil_artista_id,area_id,principal) values(3,1,true)");
        jdbc.execute("insert into perfil_artista_funcao(perfil_artista_id,area_id,funcao_id) values(3,1,1)");
        salvar(a,"PERFIL_ARTISTA",3);salvar(a,"VAGA",10);salvar(b,"PERFIL_ARTISTA",3);
        var res=mvc.perform(get("/api/salvos").header("Authorization",token(a))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.size").value(20)).andReturn();
        assertThat(res.getResponse().getContentAsString()).doesNotContain("email","cpf","cnpj","telefone","usuarioId","Endereço privado");
        for(String tipo:List.of("PERFIL_ARTISTA","VAGA")) mvc.perform(get("/api/salvos").header("Authorization",token(a)).param("tipoAlvo",tipo))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].tipoAlvo").value(tipo));
        mvc.perform(get("/api/salvos").header("Authorization",token(b))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/salvos").header("Authorization",token(b)))
                .andExpect(jsonPath("$.content[0].funcoes").value("Canto"));
    }
    @Test void paginaOrdenaEstavelmenteSemNMaisUm() throws Exception {
        jdbc.execute("insert into usuarios(id,nome,data_nascimento,telefone,email,senha,tipo_usuario,status_conta) select n,'Artista '||n,'1990-01-01','11999999999',n||'@page.test','hash','ARTISTA','ATIVA' from generate_series(100,159)n");
        jdbc.execute("insert into perfis_artistas(usuario_id,tipo_perfil_artistico) select n,'ARTISTA_SOLO' from generate_series(100,159)n");
        jdbc.execute("insert into itens_salvos(usuario_id,tipo_alvo,alvo_id,data_salvamento) select 1,'PERFIL_ARTISTA',n,timestamp '2026-09-14 12:00:00' from generate_series(100,159)n");
        var stats=emf.unwrap(SessionFactory.class).getStatistics();stats.clear();
        mvc.perform(get("/api/salvos").header("Authorization",token(a))).andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(60)).andExpect(jsonPath("$.content[0].alvoId").value(159));
        // Hibernate faz uma consulta de página/contagem, nunca uma entidade por salvo.
        // As projeções de alvos usam, adicionalmente, uma consulta JDBC por tipo presente.
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(8);
        mvc.perform(get("/api/salvos").header("Authorization",token(a)).param("size","100"))
                .andExpect(jsonPath("$.size").value(50)).andExpect(jsonPath("$.content.length()").value(50));
        mvc.perform(get("/api/salvos").header("Authorization",token(a)).param("page","1"))
                .andExpect(jsonPath("$.content[0].alvoId").value(139));
    }
    @ParameterizedTest @ValueSource(strings={"PAUSADA","ENCERRADA","CANCELADA"})
    void mudancaDeStatusMantemHistoricoSemLiberarAcesso(String statusVaga) throws Exception {
        salvar(a,"VAGA",10);
        jdbc.update("update vagas set status=cast(? as status_vaga_enum) where id=10",statusVaga);
        mvc.perform(get("/api/salvos").header("Authorization",token(a)))
                .andExpect(jsonPath("$.content[0].status").value(statusVaga))
                .andExpect(jsonPath("$.content[0].disponivel").value(false)).andExpect(jsonPath("$.content[0].href").isEmpty());
        salvar(a,"VAGA",10).andExpect(status().isOk());
        assertThat(count()).isEqualTo(1);
    }
    @Test void alvoRemovidoOuPrivadoPodeSerRemovidoDaColecao() throws Exception {
        salvar(a,"PERFIL_ARTISTA",3);
        jdbc.update("update usuarios set data_nascimento=current_date-interval '16 years' where id=3");
        var res=mvc.perform(get("/api/salvos").header("Authorization",token(a))).andExpect(jsonPath("$.content[0].disponivel").value(false)).andReturn();
        assertThat(res.getResponse().getContentAsString()).doesNotContain("Marina","São Paulo");
        jdbc.update("delete from usuarios where id=3");
        mvc.perform(delete("/api/salvos/PERFIL_ARTISTA/3").header("Authorization",token(a))).andExpect(status().isNoContent());
        assertThat(count()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"OBRA","INVALIDO"})
    void tiposForaDoEscopoSaoRejeitados(String tipo) throws Exception {
        salvar(a,tipo,3).andExpect(status().isBadRequest());
        mvc.perform(get("/api/salvos").header("Authorization",token(a)).param("tipoAlvo",tipo)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/salvos/"+tipo+"/3").header("Authorization",token(a))).andExpect(status().isBadRequest());
    }
    @Test void parametrosInvalidosNaoPersistem() throws Exception {
        salvar(a,"VAGA",0).andExpect(status().isBadRequest());
        mvc.perform(get("/api/salvos").header("Authorization",token(a)).param("size","0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/salvos").header("Authorization",token(a)).param("page","-1")).andExpect(status().isBadRequest());
        assertThat(count()).isZero();
    }
    @Test void contaPendenteNaoUsaSalvos() throws Exception {
        jdbc.update("update usuarios set status_conta='PENDENTE_VERIFICACAO_EMAIL' where id=1");
        salvar(a,"VAGA",10).andExpect(status().isForbidden());
    }
    @Test void falhaRealtimeNaoDesfazSalvoNemNotificacaoPersistida() throws Exception {
        doThrow(new IllegalStateException("offline")).when(realtime).entregar(any(),any(),any());
        salvar(a,"PERFIL_ARTISTA",3).andExpect(status().isCreated());
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notificacoes where tipo_notificacao='SALVO'",Integer.class)).isEqualTo(1);
        verify(realtime).entregar(eq(3L),any(),any());
    }
}
