package com.portifolio.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.TipoUsuario;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PerfilCompletoServiceTest {

    private final PerfilCompletoService service = new PerfilCompletoService(null, null, null);

    @Test
    void artistaCompletoExigeSomenteCamposObrigatoriosDoRf08() {
        Usuario usuario = usuarioCompleto(TipoUsuario.ARTISTA);
        PerfilArtista perfil = perfilArtistaCompleto();
        perfil.setUrlPortfolio(null);
        perfil.setTags(null);
        perfil.setBannerUrl(null);
        perfil.setNivelMedalha(null);
        perfil.setScoreEngajamento(null);

        assertThat(service.calcularArtista(usuario, perfil)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nome", "telefone", "email", "biografia", "localizacao"})
    void artistaComTextoObrigatorioAusenteOuBlankFicaIncompleto(String campo) {
        Usuario usuario = usuarioCompleto(TipoUsuario.ARTISTA);
        PerfilArtista perfil = perfilArtistaCompleto();
        aplicarTexto(campo, null, usuario, perfil);
        assertThat(service.calcularArtista(usuario, perfil)).isFalse();

        aplicarTexto(campo, "   ", usuario, perfil);
        assertThat(service.calcularArtista(usuario, perfil)).isFalse();
    }

    @Test
    void artistaSemNascimentoFicaIncompleto() {
        Usuario usuario = usuarioCompleto(TipoUsuario.ARTISTA);
        usuario.setDataNascimento(null);
        assertThat(service.calcularArtista(usuario, perfilArtistaCompleto())).isFalse();
    }

    @Test
    void contratanteNaoDependeDeNomeEmpresa() {
        Usuario usuario = usuarioCompleto(TipoUsuario.CONTRATANTE);
        PerfilContratante perfil = perfilContratanteCompleto();
        perfil.setNomeEmpresa(null);
        assertThat(service.calcularContratante(usuario, perfil)).isTrue();

        perfil.setNomeEmpresa("");
        assertThat(service.calcularContratante(usuario, perfil)).isTrue();
    }

    @Test
    void menorExigeResponsavelLegalCompleto() {
        Usuario usuario = usuarioCompleto(TipoUsuario.ARTISTA);
        usuario.setDataNascimento(LocalDate.now().minusYears(16));
        assertThat(service.calcularArtista(usuario, perfilArtistaCompleto())).isFalse();

        usuario.setNomeResponsavel("Responsável");
        usuario.setTelefoneResponsavel("11988887777");
        usuario.setEmailResponsavel("responsavel@example.com");
        assertThat(service.calcularArtista(usuario, perfilArtistaCompleto())).isTrue();
    }

    private Usuario usuarioCompleto(TipoUsuario tipo) {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuário Completo");
        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setTelefone("11999999999");
        usuario.setEmail("completo@example.com");
        usuario.setTipoUsuario(tipo);
        return usuario;
    }

    private PerfilArtista perfilArtistaCompleto() {
        PerfilArtista perfil = new PerfilArtista();
        perfil.setBiografia("Biografia");
        perfil.setLocalizacao("São Paulo");
        return perfil;
    }

    private PerfilContratante perfilContratanteCompleto() {
        PerfilContratante perfil = new PerfilContratante();
        perfil.setBiografia("Biografia");
        perfil.setLocalizacao("São Paulo");
        return perfil;
    }

    private void aplicarTexto(
            String campo, String valor, Usuario usuario, PerfilArtista perfil) {
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
