import apiClient from '../api/apiClient';
import { cadastrar, login } from './authService';

jest.mock('../api/apiClient', () => ({
  __esModule: true,
  default: { post: jest.fn() },
}));

beforeEach(() => apiClient.post.mockReset());

test('login envia somente e-mail, senha e rememberMe false para a API pública', async () => {
  apiClient.post.mockResolvedValue({ token: 'jwt' });

  await login({ email: '  pessoa@palco.test ', senha: 'segredo', artistaId: 99 });

  expect(apiClient.post).toHaveBeenCalledWith(
    '/auth/login',
    { email: 'pessoa@palco.test', senha: 'segredo', rememberMe: false },
    { token: null }
  );
});

test('cadastro de artista usa lista branca e não envia identidade ou confirmação de senha', async () => {
  apiClient.post.mockResolvedValue({ id: 1 });

  await cadastrar({
    nome: '  Artista Teste ', dataNascimento: '1990-01-01', telefone: ' 11999999999 ',
    email: ' artista@palco.test ', senha: 'senha123', confirmarSenha: 'senha123',
    tipoUsuario: 'ARTISTA', tipoPerfilArtistico: 'ARTISTA_SOLO', areaPrincipalId: 6, tipoPerfilContratante: 'Pessoa Física', termos: true,
    artistaId: 44, usuarioId: 55,
  });

  expect(apiClient.post).toHaveBeenCalledWith('/auth/cadastro', {
    nome: 'Artista Teste', dataNascimento: '1990-01-01', telefone: '11999999999',
    email: 'artista@palco.test', senha: 'senha123', tipoUsuario: 'ARTISTA', tipoPerfilArtistico: 'ARTISTA_SOLO', areaPrincipalId: 6,
    tipoPerfilContratante: null, nomeResponsavel: null,
    telefoneResponsavel: null, emailResponsavel: null,
  }, { token: null });
  const payload = apiClient.post.mock.calls[0][1];
  expect(payload).not.toHaveProperty('confirmarSenha');
  expect(payload).not.toHaveProperty('artistaId');
  expect(payload).not.toHaveProperty('usuarioId');
});

test('cadastro de menor contratante preserva perfil e responsável legal', async () => {
  apiClient.post.mockResolvedValue({ id: 2 });

  await cadastrar({
    nome: 'Jovem Contratante', dataNascimento: '2010-01-01', telefone: '11988888888',
    email: 'jovem@palco.test', senha: 'senha123', tipoUsuario: 'CONTRATANTE',
    tipoPerfilContratante: 'Pessoa Física', nomeResponsavel: 'Responsável',
    telefoneResponsavel: '11977777777', emailResponsavel: 'responsavel@palco.test',
  });

  expect(apiClient.post.mock.calls[0][1]).toMatchObject({
    tipoUsuario: 'CONTRATANTE', tipoPerfilContratante: 'Pessoa Física',
    nomeResponsavel: 'Responsável', telefoneResponsavel: '11977777777',
    emailResponsavel: 'responsavel@palco.test',
  });
});
