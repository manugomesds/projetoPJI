export const PASSWORD_POLICY_MESSAGE =
  'A senha deve ter entre 8 e 72 caracteres e conter ao menos uma letra maiúscula, uma letra minúscula, um número e um caractere especial.';

const ASCII_UPPERCASE = /[A-Z]/;
const ASCII_LOWERCASE = /[a-z]/;
const ASCII_DIGIT = /[0-9]/;
const UNICODE_ALPHANUMERIC_OR_WHITESPACE = /[\p{L}\p{N}\s]/u;

export function isPasswordValid(password) {
  if (typeof password !== 'string') return false;
  const characters = [...password];
  if (characters.length < 8 || characters.length > 72) return false;

  return ASCII_UPPERCASE.test(password)
    && ASCII_LOWERCASE.test(password)
    && ASCII_DIGIT.test(password)
    && characters.some((character) => !UNICODE_ALPHANUMERIC_OR_WHITESPACE.test(character));
}
