package com.portifolio.service;

import static org.assertj.core.api.Assertions.*;
import com.portifolio.exception.PortfolioOperationException;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PortfolioStorageServiceTest {
    @TempDir Path root;
    @ParameterizedTest @ValueSource(strings={"../evil.jpg","..\\evil.jpg","/etc/passwd","C:\\evil.jpg","https://evil.test/x.jpg","1/../../evil.jpg","1/a\u0000.jpg","2/00000000-0000-0000-0000-000000000000.jpg"})
    void caminhosInvalidosNaoLeemNemApagam(String ref) {
        var storage=new PortfolioStorageService(root.toString());
        assertThatThrownBy(() -> storage.ler(1,ref,4)).isInstanceOf(PortfolioOperationException.class);
        assertThatThrownBy(() -> storage.remover(1,ref)).isInstanceOf(PortfolioOperationException.class);
        assertThatThrownBy(() -> storage.gravar(1,ref,new byte[4])).isInstanceOf(PortfolioOperationException.class);
        assertThat(root.toFile().list()).isEmpty();
    }
    @Test void nomesAleatoriosLeituraRemocaoETemporarios() throws Exception {
        var storage=new PortfolioStorageService(root.toString());
        String a=storage.novaReferencia(1,"pdf"),b=storage.novaReferencia(1,"pdf");assertThat(a).isNotEqualTo(b);
        storage.gravar(1,a,new byte[]{1,2,3}); assertThat(storage.ler(1,a,3)).containsExactly(1,2,3);
        assertThatThrownBy(() -> storage.ler(1,a,2)).isInstanceOf(PortfolioOperationException.class);
        assertThatThrownBy(() -> storage.gravar(1,a,new byte[]{4})).isInstanceOf(PortfolioOperationException.class);
        assertThat(storage.ler(1,a,3)).containsExactly(1,2,3);
        try(var paths=Files.walk(root)) {assertThat(paths.filter(Files::isRegularFile).count()).isEqualTo(1);}
        storage.remover(1,a);assertThat(Files.exists(root.resolve(a))).isFalse();
    }
    @Test void diretorioImpossivelNaoDeixaTemporario() throws Exception {
        Files.write(root.resolve("1"),new byte[]{1});
        var storage=new PortfolioStorageService(root.toString());
        assertThatThrownBy(() -> storage.gravar(1,storage.novaReferencia(1,"png"),new byte[]{1})).isInstanceOf(PortfolioOperationException.class);
        assertThat(root.toFile().list()).containsExactly("1");
    }
    @ParameterizedTest @ValueSource(strings={"frontend","target","build","static"})
    void naoUsaDiretoriosPublicosOuDeBuild(String pasta) {
        assertThatThrownBy(() -> new PortfolioStorageService(root.resolve(pasta).toString())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejeitaLinkAntesDoAcesso() {
        var storage=new PortfolioStorageService(root.toString());
        String ref=storage.novaReferencia(1,"pdf");
        try (var files=org.mockito.Mockito.mockStatic(Files.class,org.mockito.Mockito.CALLS_REAL_METHODS)) {
            files.when(() -> Files.isSymbolicLink(root.resolve("1"))).thenReturn(true);
            assertThatThrownBy(() -> storage.ler(1,ref,4)).isInstanceOf(PortfolioOperationException.class);
            assertThatThrownBy(() -> storage.remover(1,ref)).isInstanceOf(PortfolioOperationException.class);
            assertThatThrownBy(() -> storage.gravar(1,ref,new byte[]{1})).isInstanceOf(PortfolioOperationException.class);
        }
    }
}
