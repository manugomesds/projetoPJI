import apiClient from '../api/apiClient';
import { getDashboard, getPrivateProfile, updatePrivateProfile } from './accountService';

jest.mock('../api/apiClient', () => ({ get: jest.fn(), put: jest.fn() }));

beforeEach(() => Object.values(apiClient).forEach((method) => method.mockReset()));

test('busca dashboard com o limite oficial da UI', async () => {
  apiClient.get.mockResolvedValue({ tipoUsuario: 'ARTISTA' });
  await getDashboard();
  expect(apiClient.get).toHaveBeenCalledWith('/dashboard?size=5');
});

test('carrega usuário atual, perfil artista próprio e catálogo de tags', async () => {
  apiClient.get
    .mockResolvedValueOnce({ id: 7, tipoUsuario: 'ARTISTA' })
    .mockResolvedValueOnce({ usuarioId: 7 })
    .mockResolvedValueOnce([{ id: 2, nome: 'Música' }]);
  await expect(getPrivateProfile()).resolves.toEqual({ usuario: { id: 7, tipoUsuario: 'ARTISTA' }, perfil: { usuarioId: 7 }, tags: [{ id: 2, nome: 'Música' }] });
  expect(apiClient.get).toHaveBeenNthCalledWith(1, '/usuarios/me');
  expect(apiClient.get).toHaveBeenNthCalledWith(2, '/perfis-artistas/7');
  expect(apiClient.get).toHaveBeenNthCalledWith(3, '/funcoes');
});

test('carrega contratante próprio sem buscar tags de artista', async () => {
  apiClient.get.mockResolvedValueOnce({ id: 8, tipoUsuario: 'CONTRATANTE' }).mockResolvedValueOnce({ usuarioId: 8 });
  await getPrivateProfile();
  expect(apiClient.get).toHaveBeenCalledTimes(2);
  expect(apiClient.get).toHaveBeenLastCalledWith('/perfis-contratantes/8');
});

test('atualiza artista usando somente ID vindo do usuário atual e whitelist de campos', async () => {
  apiClient.put.mockResolvedValue({ id: 7, perfilCompleto: true });
  await updatePrivateProfile({ id: 7, tipoUsuario: 'ARTISTA', dataNascimento: '1990-01-01' }, {
    nome: ' Artista ', telefone: ' 1199 ', email: ' artista@test ', biografia: ' Bio ', localizacao: ' SP ',
    bannerUrl: '', urlPortfolio: ' https://portfolio.test ', areaPrincipalId: 6, funcaoIds: ['3'], senhaAtual: 'atual', novaSenha: 'nova',
  });
  expect(apiClient.put).toHaveBeenNthCalledWith(1, '/perfis-artistas/7', {
    usuarioId: 7, biografia: 'Bio', localizacao: 'SP', bannerUrl: null,
    urlPortfolio: 'https://portfolio.test', areaPrincipalId: 6, funcaoIds: [3],
  });
  expect(apiClient.put).toHaveBeenNthCalledWith(2, '/usuarios/me', {
    nome: 'Artista', dataNascimento: '1990-01-01', telefone: '1199', email: 'artista@test', senhaAtual: 'atual', novaSenha: 'nova',
  });
  expect(JSON.stringify(apiClient.put.mock.calls)).not.toContain('perfilCompleto');
});

test('atualiza campos próprios do contratante sem campos de artista', async () => {
  apiClient.put.mockResolvedValue({ id: 9 });
  await updatePrivateProfile({ id: 9, tipoUsuario: 'CONTRATANTE', dataNascimento: '1980-01-01' }, {
    nome: 'Dono', telefone: '11', email: 'dono@test', biografia: 'Bio', localizacao: 'RJ', bannerUrl: '',
    nomeEmpresa: ' Empresa ', tipoPerfil: ' Produtora ', senhaAtual: '', novaSenha: '',
  });
  expect(apiClient.put.mock.calls[0][1]).toEqual({ usuarioId: 9, biografia: 'Bio', localizacao: 'RJ', bannerUrl: null, nomeEmpresa: 'Empresa', tipoPerfil: 'Produtora' });
  expect(apiClient.put.mock.calls[0][1]).not.toHaveProperty('urlPortfolio');
  expect(apiClient.put.mock.calls[0][1]).not.toHaveProperty('funcaoIds');
});
