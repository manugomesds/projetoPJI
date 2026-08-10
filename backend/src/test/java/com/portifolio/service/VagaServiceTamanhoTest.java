package com.portifolio.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

// Teste unitario puro (sem contexto Spring, sem banco) - normalizarTamanho() e
// logica pura de RNF12 (default 20, maximo 50). Reflection usada porque o metodo
// e private; se preferirem, trocar para package-private no VagaService remove
// a necessidade de reflection aqui.
class VagaServiceTamanhoTest {

    private final VagaService service = new VagaService(null, null, null, null, null, null);

    @ParameterizedTest
    @CsvSource({
            "0, 20",
            "-5, 20",
            "1, 1",
            "20, 20",
            "50, 50",
            "51, 50",
            "999, 50",
    })
    void deveNormalizarTamanhoCorretamente(int solicitado, int esperado) throws Exception {
        Method metodo = VagaService.class.getDeclaredMethod("normalizarTamanho", Integer.class);
        metodo.setAccessible(true);

        int resultado = (int) metodo.invoke(service, solicitado);

        assertThat(resultado).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({ "true, 20" })
    void deveUsarPadraoQuandoTamanhoForNulo(boolean ignorar, int esperado) throws Exception {
        Method metodo = VagaService.class.getDeclaredMethod("normalizarTamanho", Integer.class);
        metodo.setAccessible(true);

        int resultado = (int) metodo.invoke(service, new Object[]{null});

        assertThat(resultado).isEqualTo(esperado);
    }
}
