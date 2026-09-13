package com.portifolio.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Funcao;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.TipoUsuario;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PerfilCompletoServiceTest {

    private final PerfilCompletoService service = new PerfilCompletoService(null, null, null);

    @Test
    void artistaCompletoNaoDependeDeCamposOpcionais() {
        Usuario usuario = usuarioCompleto(TipoUsuario.ARTISTA);
        PerfilArtista perfil = perfilArtistaCompleto();
        perfil.setBannerUrl(null);

        assertThat(service.calcularArtista(usuario, perfil)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nome", "telefone", "email", "biografia", "localizacao", "portfolio"})
    void artistaComTextoObrigatorioAusenteOuBlankFicaIncompleto(String campo) {
        Usuario usuario = usuarioCompleto(TipoUsuario.ARTISTA);
        PerfilArtista perfil = perfilArtistaCompleto();
        aplicarTexto(campo, null, usuario, perfil);
        assertThat(service.calcularArtista(usuario, perfil)).isFalse();

        aplicarTexto(campo, "   ", usuario, perfil);
        assertThat(service.calcularArtista(usuario, perfil)).isFalse();
    }

    @Test
    void artistaSemNascimentoOuCredencialFicaIncompleto() {
        Usuario usuario = usuarioCompleto(TipoUsuario.ARTISTA);
        PerfilArtista perfil = perfilArtistaCompleto();
        usuario.setDataNascimento(null);
        assertThat(service.calcularArtista(usuario, perfil)).isFalse();

        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setSenha(null);
        usuario.setGoogleId(null);
        assertThat(service.calcularArtista(usuario, perfil)).isFalse();
    }

    @Test
    void artistaGoogleDispensaSenhaLocal() {
        Usuario usuario = usuarioCompleto(TipoUsuario.ARTISTA);
        usuario.setSenha(null);
        usuario.setGoogleId("google-123");

        assertThat(service.calcularArtista(usuario, perfilArtistaCompleto())).isTrue();
    }

    @Test
    void artistaSemFuncoesFicaIncompletoEComUmaFuncaoFicaCompleto() {
        Usuario usuario = usuarioCompleto(TipoUsuario.ARTISTA);
        PerfilArtista perfil = perfilArtistaCompleto();
        com.portifolio.support.OfficialSchemaFixtures.funcoes(perfil, new HashSet<>());
        assertThat(service.calcularArtista(usuario, perfil)).isFalse();

        com.portifolio.support.OfficialSchemaFixtures.funcoes(perfil, Set.of(new Funcao()));
        assertThat(service.calcularArtista(usuario, perfil)).isTrue();
    }

    @Test
    void contratanteCompletoNaoDependeDeNomeEmpresaNemCamposOpcionais() {
        Usuario usuario = usuarioCompleto(TipoUsuario.CONTRATANTE);
        PerfilContratante perfil = perfilContratanteCompleto();
        perfil.setNomeEmpresa(null);
        perfil.setBannerUrl(null);
        assertThat(service.calcularContratante(usuario, perfil)).isTrue();

        perfil.setNomeEmpresa("");
        assertThat(service.calcularContratante(usuario, perfil)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nome", "telefone", "email", "biografia", "localizacao"})
    void contratanteComTextoObrigatorioAusenteOuBlankFicaIncompleto(String campo) {
        Usuario usuario = usuarioCompleto(TipoUsuario.CONTRATANTE);
        PerfilContratante perfil = perfilContratanteCompleto();
        aplicarTextoContratante(campo, null, usuario, perfil);
        assertThat(service.calcularContratante(usuario, perfil)).isFalse();

        aplicarTextoContratante(campo, "  ", usuario, perfil);
        assertThat(service.calcularContratante(usuario, perfil)).isFalse();
    }

    @Test
    void contratanteSemNascimentoOuCredencialFicaIncompleto() {
        Usuario usuario = usuarioCompleto(TipoUsuario.CONTRATANTE);
        PerfilContratante perfil = perfilContratanteCompleto();
        usuario.setDataNascimento(null);
        assertThat(service.calcularContratante(usuario, perfil)).isFalse();

        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setSenha(null);
        usuario.setGoogleId(null);
        assertThat(service.calcularContratante(usuario, perfil)).isFalse();
    }

    private Usuario usuarioCompleto(TipoUsuario tipo) {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuário Completo");
        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setTelefone("11999999999");
        usuario.setEmail("completo@example.com");
        usuario.setSenha("hash-presente");
        usuario.setTipoUsuario(tipo);
        return usuario;
    }

    private PerfilArtista perfilArtistaCompleto() {
        PerfilArtista perfil = new PerfilArtista();
        perfil.setTipoPerfilArtistico(com.portifolio.model.enums.TipoPerfilArtistico.ARTISTA_SOLO);
        perfil.setRaioAtuacao(com.portifolio.model.enums.Abrangencia.LOCAL);
        perfil.setBiografia("Biografia");
        perfil.setLocalizacao("São Paulo");
        perfil.setUrlPortfolio("https://portfolio.example");
        com.portifolio.support.OfficialSchemaFixtures.funcoes(perfil, Set.of(new Funcao()));
        return perfil;
    }

    private PerfilContratante perfilContratanteCompleto() {
        PerfilContratante perfil = new PerfilContratante();
        perfil.setBiografia("Biografia");
        perfil.setLocalizacao("São Paulo");
        return perfil;
    }

    private void aplicarTexto(String campo, String valor, Usuario usuario, PerfilArtista perfil) {
        switch (campo) {
            case "nome" -> usuario.setNome(valor);
            case "telefone" -> usuario.setTelefone(valor);
            case "email" -> usuario.setEmail(valor);
            case "biografia" -> perfil.setBiografia(valor);
            case "localizacao" -> perfil.setLocalizacao(valor);
            case "portfolio" -> perfil.setUrlPortfolio(valor);
            default -> throw new IllegalArgumentException(campo);
        }
    }

    private void aplicarTextoContratante(
            String campo, String valor, Usuario usuario, PerfilContratante perfil) {
        switch (campo) {
            case "nome" -> usuario.setNome(valor);
            case "telefone" -> usuario.setTelefone(valor);
            case "email" -> usuario.setEmail(valor);
            case "biografia" -> perfil.setBiografia(valor);
            case "localizacao" -> perfil.setLocalizacao(valor);
            default -> throw new IllegalArgumentException(campo);
        }
    }
}
