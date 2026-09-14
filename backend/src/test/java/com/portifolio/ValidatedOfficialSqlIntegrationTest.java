package com.portifolio;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Executa SQL real da integração seletiva; não certifica os arquivos bloqueados do main. */
@Testcontainers
class ValidatedOfficialSqlIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withInitScripts("db/schema-test.sql", "db/catalogo-test.sql");

    @BeforeAll static void fixtures() throws Exception {
        try (Connection c=connection(); Statement s=c.createStatement()) {
            s.execute("insert into usuarios(id,nome,data_nascimento,telefone,email,senha,tipo_usuario) values (1,'Contratante','1990-01-01','11999999999','c@sql.test','hash','CONTRATANTE'),(2,'Artista','1990-01-01','11999999999','a@sql.test','hash','ARTISTA')");
            s.execute("insert into perfis_contratantes(usuario_id) values(1)");
            s.execute("insert into perfis_artistas(usuario_id,tipo_perfil_artistico) values(2,'ARTISTA_SOLO')");
            s.execute(Files.readString(Path.of("../database/03_functions/fn_buscar_vagas.sql")));
            s.execute("insert into vagas(contratante_id,area_id,titulo,descricao,requisitos,cidade,estado,tipo_contrato,abrangencia,status,data_publicacao) select 1,1,'Vaga '||n,'Descricao','Requisitos','Sao Paulo','SP','Projeto','LOCAL','ABERTA',timestamp '2026-09-14 12:00:00' from generate_series(1,56) n");
        }
    }

    @Test void usuarioRecebeEstadoPendenteOficial() throws Exception {
        try (Connection c=connection(); Statement s=c.createStatement(); ResultSet r=s.executeQuery("select status_conta from usuarios where id=1")) {
            assertThat(r.next()).isTrue(); assertThat(r.getString(1)).isEqualTo("PENDENTE_VERIFICACAO_EMAIL");
        }
    }

    @Test void buscaOficialLimitaCinquentaEPaginaSemRepetir() throws Exception {
        try (Connection c=connection(); Statement s=c.createStatement()) {
            try (ResultSet r=s.executeQuery("select count(*),min(vaga_id),max(vaga_id) from fn_buscar_vagas(p_limit=>100)")) {
                r.next(); assertThat(r.getInt(1)).isEqualTo(50); assertThat(r.getLong(2)).isEqualTo(7); assertThat(r.getLong(3)).isEqualTo(56);
            }
            try (ResultSet r=s.executeQuery("select count(*),max(vaga_id) from fn_buscar_vagas(p_limit=>100,p_cursor_data_publicacao=>timestamp '2026-09-14 12:00:00',p_cursor_id=>7::bigint)")) {
                r.next(); assertThat(r.getInt(1)).isEqualTo(6); assertThat(r.getLong(2)).isEqualTo(6);
            }
        }
    }

    static Stream<Arguments> limites() {
        return Stream.of(new Object[]{"image/jpeg",5242880},new Object[]{"image/jpg",5242880},
                new Object[]{"image/png",5242880},new Object[]{"application/pdf",10485760},new Object[]{"audio/mpeg",20971520})
                .flatMap(v -> Stream.of(1,(int)v[1],0,-1,(int)v[1]+1)
                        .map(size -> Arguments.of(v[0],size,size>0 && size<=(int)v[1])));
    }

    @ParameterizedTest @MethodSource("limites")
    void metadataRespeitaLimitesOficiais(String mime,int tamanho,boolean permitido) throws Exception {
        try (Connection c=connection(); PreparedStatement s=c.prepareStatement("insert into portfolio_arquivos(artista_id,url_arquivo,nome_original,tamanho_bytes,tipo_mime) values(2,'audit','audit',?,?)")) {
            s.setInt(1,tamanho); s.setString(2,mime);
            if (permitido) assertThat(s.executeUpdate()).isEqualTo(1);
            else assertThatThrownBy(s::executeUpdate).isInstanceOf(SQLException.class)
                    .extracting(e -> ((SQLException)e).getSQLState()).isEqualTo("23514");
        }
    }

    @Test void rejeitaMimeForaDoCatalogo() throws Exception {
        try (Connection c=connection(); Statement s=c.createStatement()) {
            assertThatThrownBy(() -> s.executeUpdate("insert into portfolio_arquivos(artista_id,url_arquivo,nome_original,tamanho_bytes,tipo_mime) values(2,'audit','audit',1,'video/mp4')"))
                    .isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo("23514");
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
    }
}
