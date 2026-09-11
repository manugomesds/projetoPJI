package com.portifolio.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portifolio.exception.UnprocessableEntityException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordPolicyTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final PasswordPolicy policy = new PasswordPolicy(encoder);

    @Test
    void aceitaLimitesFuncionaisDeOitoESetentaEDoisCaracteres() {
        assertThat(policy.isValid("Aa1!bcde")).isTrue();
        assertThat(policy.isValid("Aa1!" + "x".repeat(68))).isTrue();
    }

    @Test
    void aceitaEspacoInternoComEspecialRealEMultiplosEspeciais() {
        assertThat(policy.isValid("Palco @2026")).isTrue();
        assertThat(policy.isValid("Aa1!@#$b")).isTrue();
    }

    @Test
    void rejeitaLimitesInvalidosVazioENulo() {
        assertThat(policy.isValid("Aa1!bcd")).isFalse();
        assertThat(policy.isValid("Aa1!" + "x".repeat(69))).isFalse();
        assertThat(policy.isValid("")).isFalse();
        assertThat(policy.isValid(null)).isFalse();
    }

    @Test
    void rejeitaAusenciaDeCadaGrupoObrigatorio() {
        assertThat(policy.isValid("artista123")).isFalse();
        assertThat(policy.isValid("PALCO@2026")).isFalse();
        assertThat(policy.isValid("PalcoPalco!")).isFalse();
        assertThat(policy.isValid("Palco2026")).isFalse();
    }

    @Test
    void whitespaceNaoContaComoEspecial() {
        assertThat(policy.isValid("Aa1 bcde")).isFalse();
        assertThat(policy.isValid("Aa1\tbcde")).isFalse();
        assertThat(policy.isValid("Aa1\nbcde")).isFalse();
    }

    @Test
    void preservaEspacosNasExtremidades() {
        String password = " Aa1!bc ";

        assertThat(policy.isValid(password)).isTrue();
        String hash = policy.encode(password);
        assertThat(encoder.matches(password, hash)).isTrue();
        assertThat(encoder.matches(password.trim(), hash)).isFalse();
    }

    @Test
    void naoNormalizaUnicodeEExigeClassesAsciiExatas() {
        assertThat(policy.isValid("Áa1!bcde")).isFalse();
        assertThat(policy.isValid("AÁ1!BCDE")).isFalse();
        assertThat(policy.isValid("Aa١!bcde")).isFalse();
        assertThat(policy.isValid("Aa1ébcde")).isFalse();
    }

    @Test
    void rejeitaComAMensagemOficial() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> policy.validateOrThrow("artista123"))
                .withMessage(PasswordPolicy.MESSAGE);
    }

    @Test
    void codificaSenhaValidaSemAlterarOValor() {
        String password = "Palco@2026";

        String hash = policy.encode(password);

        assertThat(hash).startsWith("$2").doesNotContain(password);
        assertThat(encoder.matches(password, hash)).isTrue();
    }

    @Test
    void limiteTecnicoDoBCryptEhControladoSemVirarRegraFuncionalOculta() {
        String exatamenteSetentaEDoisBytes = "Aa1!" + "á".repeat(34);
        String setentaEQuatroBytes = "Aa1!" + "á".repeat(35);

        assertThat(policy.isValid(exatamenteSetentaEDoisBytes)).isTrue();
        assertThat(policy.isValid(setentaEQuatroBytes)).isTrue();
        assertThat(encoder.matches(
                exatamenteSetentaEDoisBytes, policy.encode(exatamenteSetentaEDoisBytes))).isTrue();
        assertThatThrownBy(() -> policy.encode(setentaEQuatroBytes))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessage(PasswordPolicy.BCRYPT_LIMIT_MESSAGE)
                .hasMessageNotContaining("72 bytes");
    }
}
