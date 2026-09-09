import apiClient from '../api/apiClient';
import { getSuggestedArtists } from './vagaService';

jest.mock('../api/apiClient', () => ({
  __esModule: true,
  default: { get: jest.fn() },
}));

beforeEach(() => {
  apiClient.get.mockReset();
});

test('consulta candidatos sem enviar identidade do contratante', async () => {
  apiClient.get.mockResolvedValue({ content: [] });

  await getSuggestedArtists(42, { page: -2, size: 99 });

  expect(apiClient.get).toHaveBeenCalledWith('/vagas/42/candidaturas?page=0&size=50');
  const path = apiClient.get.mock.calls[0][0];
  expect(path).not.toContain('contratanteId');
  expect(path).not.toContain('usuarioId');
});
