package com.portifolio;

import static org.assertj.core.api.Assertions.*;

import com.portifolio.model.*;
import com.portifolio.model.enums.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@Transactional
class OfficialSchemaMappingIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScripts("db/schema-test.sql", "db/catalogo-test.sql")
            .withUrlParam("stringtype", "unspecified");

    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;

    @Test
    void contextoValidaDdlOficialSemDivergenciaDoSchemaDeTeste() throws Exception {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        Path database = Path.of("../database");
        StringBuilder expected = new StringBuilder();
        expected.append("-- Fonte: database/01_types/01_enums.sql\n")
                .append(normalizarSql(Files.readString(database.resolve("01_types/01_enums.sql"))));
        try (var paths = Files.list(database.resolve("02_tables"))) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                expected.append("\n\n-- Fonte: database/02_tables/").append(file.getFileName()).append("\n")
                        .append(normalizarSql(Files.readString(file)));
            }
        }
        assertThat(Files.readString(Path.of("src/test/resources/db/schema-test.sql")).replace("\r\n", "\n"))
                .isEqualTo(expected.append("\n").toString());
        assertThat(jdbc.queryForList("select tablename from pg_tables where schemaname='public'", String.class))
                .doesNotContain("tags", "tags_artista", "tags_vaga");
        assertThat(jdbc.queryForObject("select is_nullable from information_schema.columns where table_schema='public' and table_name='perfis_artistas' and column_name='raio_atuacao'", String.class))
                .isEqualTo("YES");
        // Bloqueio RF24 documentado: a única alteração autorizada não flexibiliza usuarios.
        assertThat(jdbc.queryForList("select is_nullable from information_schema.columns where table_schema='public' and table_name='usuarios' and column_name in ('tipo_usuario','data_nascimento','telefone')", String.class))
                .containsExactlyInAnyOrder("NO", "NO", "NO");
    }

    @Test
    void persisteEReleUsuarioResponsavelTaxonomiaVagaECandidatura() {
        Usuario artista = usuario("mapping-artista@example.test", TipoUsuario.ARTISTA);
        artista.setSenha(null);
        artista.setGoogleId("mapping-google-sub");
        artista.setEmailVerificado(true);
        artista.setStatusConta(StatusConta.ATIVA);
        artista.setFotoPerfil("https://example.test/avatar.png");
        artista.setNomeResponsavel("Responsável de teste");
        artista.setTelefoneResponsavel("11900000000");
        artista.setEmailResponsavel("responsavel@example.test");
        em.persist(artista);
        PerfilArtista perfil = new PerfilArtista();
        perfil.setUsuario(artista);
        perfil.setTipoPerfilArtistico(TipoPerfilArtistico.BANDA);
        perfil.setRaioAtuacao(Abrangencia.NACIONAL);
        em.persist(perfil);
        AreaArtistica musica = em.find(AreaArtistica.class, (short) 1);
        Funcao funcao = new Funcao();
        funcao.setNome("Instrumentista");
        funcao.setArea(musica);
        Especializacao especializacao = new Especializacao();
        especializacao.setNome("Piano");
        em.persist(especializacao);
        funcao.getEspecializacoes().add(especializacao);
        em.persist(funcao);
        PerfilArtistaArea area = new PerfilArtistaArea();
        area.setPerfil(perfil);
        area.setArea(musica);
        area.setPrincipal(true);
        area.setNivelExperiencia(NivelExperiencia.ESPECIALISTA);
        area.setFuncoes(Set.of(funcao));
        area.setEspecializacoes(Set.of(especializacao));
        perfil.getAreas().add(area);
        em.persist(area);

        Usuario contratante = usuario("mapping-contratante@example.test", TipoUsuario.CONTRATANTE);
        em.persist(contratante);
        PerfilContratante empresa = new PerfilContratante();
        empresa.setUsuario(contratante);
        empresa.setTipoPerfil("SETOR_PRIVADO");
        em.persist(empresa);
        CategoriaAfirmativa categoria = new CategoriaAfirmativa();
        categoria.setNome("Categoria de teste");
        em.persist(categoria);
        Vaga vaga = new Vaga();
        vaga.setContratante(empresa);
        vaga.setArea(musica);
        vaga.setTitulo("Vaga estrutural");
        vaga.setDescricao("Descrição");
        vaga.setRequisitos("Requisitos");
        vaga.setFormaRemuneracao(FormaRemuneracao.POR_EVENTO);
        vaga.setValorMinimo(new BigDecimal("100.00"));
        vaga.setValorMaximo(new BigDecimal("200.00"));
        vaga.setCidade("São Paulo");
        vaga.setEstado("SP");
        vaga.setTipoContrato("Freelance");
        vaga.setExperiencia("EXPERIENTE");
        vaga.setAbrangencia(Abrangencia.REGIONAL);
        vaga.setModeloTrabalho(ModeloTrabalho.HIBRIDO);
        vaga.setDataLimiteCandidatura(LocalDate.of(2027, 1, 1));
        vaga.setFuncoes(Set.of(funcao));
        vaga.setEspecializacoes(Set.of(especializacao));
        vaga.setCategoriasAfirmativas(Set.of(categoria));
        vaga.getFotos().add("https://example.test/vaga.png");
        em.persist(vaga);
        Candidatura candidatura = new Candidatura();
        candidatura.setVaga(vaga);
        candidatura.setArtista(perfil);
        candidatura.setMensagemApresentacao("Apresentação");
        candidatura.setLinkPortfolioCandidatura("https://example.test/portfolio");
        candidatura.setStatus(StatusCandidatura.ACEITA);
        em.persist(candidatura);
        em.flush();
        em.clear();

        Usuario relido = em.find(Usuario.class, artista.getId());
        assertThat(relido.getSenha()).isNull();
        assertThat(relido.getStatusConta()).isEqualTo(StatusConta.ATIVA);
        assertThat(relido.getResponsavelLegal().getUsuario().getId()).isEqualTo(relido.getId());
        PerfilArtista artistaRelido = em.find(PerfilArtista.class, artista.getId());
        assertThat(artistaRelido.getAreas()).singleElement().satisfies(a -> {
            assertThat(a.getNivelExperiencia()).isEqualTo(NivelExperiencia.ESPECIALISTA);
            assertThat(a.getFuncoes()).extracting(Funcao::getNome).containsExactly("Instrumentista");
            assertThat(a.getEspecializacoes()).extracting(Especializacao::getNome).containsExactly("Piano");
        });
        Vaga relida = em.find(Vaga.class, vaga.getId());
        assertThat(relida.getStatus()).isEqualTo(StatusVaga.RASCUNHO);
        assertThat(relida.getDataLimiteCandidatura()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(relida.getEspecializacoes()).hasSize(1);
        assertThat(relida.getCategoriasAfirmativas()).hasSize(1);
        assertThat(relida.getFotos()).containsExactly("https://example.test/vaga.png");
        assertThat(jdbc.queryForObject("select status::text from candidaturas where id=?", String.class, candidatura.getId()))
                .isEqualTo("ACEITA");
        em.find(Candidatura.class, candidatura.getId()).setStatus(StatusCandidatura.REJEITADA);
        em.flush();
        assertThat(jdbc.queryForObject("select status::text from candidaturas where id=?", String.class, candidatura.getId()))
                .isEqualTo("REJEITADA");

        // Cada tentativa usa savepoint; a violação real vem do PostgreSQL oficial.
        rejeitaConstraint("update vagas set valor_maximo=0 where id=" + vaga.getId(), "23514");
        rejeitaConstraint("update usuarios set senha=null,google_id=null where id=" + artista.getId(), "23514");
        rejeitaConstraint("insert into perfil_artista_area(perfil_artista_id,area_id,principal) values ("
                + artista.getId() + ",2,true)", "23505");
        Long funcaoOutraArea = jdbc.queryForObject(
                "insert into funcoes(area_id,nome) values (2,'Outra área') returning id", Long.class);
        rejeitaConstraint("insert into perfil_artista_funcao(perfil_artista_id,area_id,funcao_id) values ("
                + artista.getId() + ",1," + funcaoOutraArea + ")", "23503");
        rejeitaConstraint("insert into candidaturas(vaga_id,artista_id,mensagem_apresentacao,link_portfolio_candidatura) "
                + "select vaga_id,artista_id,mensagem_apresentacao,link_portfolio_candidatura from candidaturas where id="
                + candidatura.getId(), "23505");
    }

    @Test
    void enumsPersistidosCorrespondemAosValoresPostgresql() {
        conferirEnum("tipo_usuario_enum", TipoUsuario.values());
        conferirEnum("modelo_trabalho_enum", ModeloTrabalho.values());
        conferirEnum("status_vaga_enum", StatusVaga.values());
        conferirEnum("status_candidatura_enum", StatusCandidatura.values());
        conferirEnum("tipo_notificacao_enum", TipoNotificacao.values());
        conferirEnum("status_conta_enum", StatusConta.values());
        conferirEnum("nivel_experiencia_enum", NivelExperiencia.values());
        conferirEnum("abrangencia_enum", Abrangencia.values());
        conferirEnum("forma_remuneracao_enum", FormaRemuneracao.values());
        conferirEnum("tipo_perfil_artistico_enum", TipoPerfilArtistico.values());
        assertThat(jdbc.queryForObject("select data_type from information_schema.columns "
                + "where table_name='vagas' and column_name='experiencia'", String.class)).isEqualTo("character varying");
        assertThat(jdbc.queryForObject("select data_type from information_schema.columns "
                + "where table_name='perfis_contratantes' and column_name='tipo_perfil'", String.class)).isEqualTo("character varying");
    }

    private void conferirEnum(String tipo, DatabaseEnum[] valores) {
        var oficiais = jdbc.queryForList("select e.enumlabel from pg_enum e join pg_type t on t.oid=e.enumtypid "
                + "where t.typname=? order by e.enumsortorder", String.class, tipo);
        assertThat(java.util.Arrays.stream(valores).map(DatabaseEnum::getDatabaseValue).toList())
                .containsExactlyElementsOf(oficiais);
    }

    private void rejeitaConstraint(String sql, String sqlState) {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            var savepoint = connection.setSavepoint();
            try (var statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.executeUpdate(sql))
                        .isInstanceOf(java.sql.SQLException.class)
                        .satisfies(error -> assertThat(((java.sql.SQLException) error).getSQLState()).isEqualTo(sqlState));
            } finally {
                connection.rollback(savepoint);
            }
            return null;
        });
    }

    private Usuario usuario(String email, TipoUsuario tipo) {
        Usuario usuario = new Usuario();
        usuario.setNome("Teste de mapping");
        usuario.setEmail(email);
        usuario.setTelefone("11999999999");
        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setSenha("hash-de-fixture");
        usuario.setTipoUsuario(tipo);
        return usuario;
    }

    private String normalizarSql(String sql) {
        return sql.replace("\r\n", "\n").replace("\uFEFF", "")
                .replaceAll("(?m)[\\t ]+$", "").trim();
    }
}
