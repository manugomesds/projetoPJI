package com.portifolio.validation;

import com.portifolio.exception.UnprocessableEntityException;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    public static final String MESSAGE =
            "A senha deve ter entre 8 e 72 caracteres e conter ao menos uma letra maiúscula, "
                    + "uma letra minúscula, um número e um caractere especial.";
    public static final String BCRYPT_LIMIT_MESSAGE =
            "A senha atende à política funcional, mas não pode ser processada com segurança "
                    + "pelo mecanismo de proteção atual.";

    private static final int BCRYPT_MAX_BYTES = 72;

    private final BCryptPasswordEncoder passwordEncoder;

    public PasswordPolicy(BCryptPasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public boolean isValid(String password) {
        if (password == null) {
            return false;
        }

        int length = password.codePointCount(0, password.length());
        if (length < 8 || length > 72) {
            return false;
        }

        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (int offset = 0; offset < password.length();) {
            int codePoint = password.codePointAt(offset);
            if (codePoint >= 'A' && codePoint <= 'Z') {
                hasUppercase = true;
            } else if (codePoint >= 'a' && codePoint <= 'z') {
                hasLowercase = true;
            } else if (codePoint >= '0' && codePoint <= '9') {
                hasDigit = true;
            } else if (!isAlphanumeric(codePoint)
                    && !Character.isWhitespace(codePoint)
                    && !Character.isSpaceChar(codePoint)) {
                hasSpecial = true;
            }
            offset += Character.charCount(codePoint);
        }

        return hasUppercase && hasLowercase && hasDigit && hasSpecial;
    }

    private boolean isAlphanumeric(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isLetter(codePoint)
                || type == Character.DECIMAL_DIGIT_NUMBER
                || type == Character.LETTER_NUMBER
                || type == Character.OTHER_NUMBER;
    }

    public void validateOrThrow(String password) {
        if (!isValid(password)) {
            throw new IllegalArgumentException(MESSAGE);
        }
    }

    public String encode(String password) {
        validateOrThrow(password);
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new UnprocessableEntityException(BCRYPT_LIMIT_MESSAGE);
        }
        try {
            return passwordEncoder.encode(password);
        } catch (IllegalArgumentException ex) {
            throw new UnprocessableEntityException(BCRYPT_LIMIT_MESSAGE);
        }
    }
}
