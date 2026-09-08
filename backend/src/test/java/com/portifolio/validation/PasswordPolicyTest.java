package com.portifolio.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void aceitaSenhasQueCumpremTodaAPolitica() {
        assertThat(policy.isValid("Palco@2026")).isTrue();
        assertThat(policy.isValid("Palc@123")).isTrue();
        assertThat(policy.isValid("Aa1!" + "x".repeat(68))).isTrue();
        assertThat(policy.isValid("Palco_2026")).isTrue();
        assertThat(policy.isValid("Palco @2026")).isTrue();
    }

    @Test
    void rejeitaCadaCategoriaInvalidaSemNormalizarASenha() {
        assertThat(policy.isValid("Pa@1234")).isFalse();
        assertThat(policy.isValid("Aa1!" + "x".repeat(69))).isFalse();
        assertThat(policy.isValid("palco@2026")).isFalse();
        assertThat(policy.isValid("PALCO@2026")).isFalse();
        assertThat(policy.isValid("PalcoPalco!")).isFalse();
        assertThat(policy.isValid("Palco2026")).isFalse();
        assertThat(policy.isValid("        ")).isFalse();
        assertThat(policy.isValid("Palco 2026")).isFalse();
        assertThat(policy.isValid("SomenteLetras")).isFalse();
        assertThat(policy.isValid("12345678")).isFalse();
        assertThat(policy.isValid("Pálco2026")).isFalse();
        assertThat(policy.isValid("Palco12²")).isFalse();
        assertThat(policy.isValid(null)).isFalse();
    }

    @Test
    void rejeitaComAMensagemOficial() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> policy.validateOrThrow("artista123"))
                .withMessage(PasswordPolicy.MESSAGE);
    }
}
