import apiClient from '../api/apiClient';
import { createCandidatura, withdrawCandidatura, analyzeCandidatura } from './candidaturaService';

jest.mock('../api/apiClient', () => ({
  __esModule: true,
  default: { post: jest.fn(), delete: jest.fn(), put: jest.fn() },
}));

beforeEach(() => {
  apiClient.post.mockReset();
});

test('retirada envia somente o identificador da candidatura na rota existente', async () => {
  await withdrawCandidatura(9);
  expect(apiClient.delete).toHaveBeenCalledWith('/candidaturas/9');
});

test('análise usa a rota existente e os vínculos retornados pela API sem ownerId', async () => {
  await analyzeCandidatura('42', { candidaturaId: 9, artistaId: 5, mensagemApresentacao: 'Mensagem', linkPortfolioCandidatura: 'https://example.com', ownerId: 999 }, 'EM_ANALISE');
  expect(apiClient.put).toHaveBeenCalledWith('/candidaturas/9', {
    vagaId: 42, artistaId: 5, mensagemApresentacao: 'Mensagem', linkPortfolioCandidatura: 'https://example.com', status: 'EM_ANALISE',
  });
});

test('envia somente os campos oficiais e converte vagaId para número', async () => {
  apiClient.post.mockResolvedValue({ id: 9, status: 'PENDENTE' });

  await createCandidatura({
    vagaId: '42',
    mensagemApresentacao: 'Minha apresentação',
    linkPortfolioCandidatura: 'https://portfolio.example',
    artistaId: 777,
    usuarioId: 888,
  });

  expect(apiClient.post).toHaveBeenCalledWith('/candidaturas', {
    vagaId: 42,
    mensagemApresentacao: 'Minha apresentação',
    linkPortfolioCandidatura: 'https://portfolio.example',
  });
  const payload = apiClient.post.mock.calls[0][1];
  expect(payload).not.toHaveProperty('artistaId');
  expect(payload).not.toHaveProperty('usuarioId');
});
