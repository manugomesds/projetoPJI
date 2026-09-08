import { isPasswordValid, PASSWORD_POLICY_MESSAGE } from './passwordPolicy';

test.each([
  'Palco@2026',
  'Palc@123',
  `Aa1!${'x'.repeat(68)}`,
  'Palco_2026',
  'Palco @2026',
])('aceita senha válida %s', (password) => {
  expect(isPasswordValid(password)).toBe(true);
});

test.each([
  'Pa@1234',
  `Aa1!${'x'.repeat(69)}`,
  'artista123',
  'PALCO@2026',
  'PalcoPalco!',
  'Palco2026',
  '        ',
  'Palco 2026',
  'SomenteLetras',
  '12345678',
  'Pálco2026',
  'Palco12²',
  null,
])('rejeita senha inválida sem normalização: %s', (password) => {
  expect(isPasswordValid(password)).toBe(false);
});

test('exporta a mensagem oficial única', () => {
  expect(PASSWORD_POLICY_MESSAGE).toBe(
    'A senha deve ter entre 8 e 72 caracteres e conter ao menos uma letra maiúscula, uma letra minúscula, um número e um caractere especial.'
  );
});
