import apiClient from '../api/apiClient';
import {
  cancelVacancy,
  changeVacancyStatus,
  createVacancy,
  getManagedVacancy,
  listAllReceivedApplications,
  listMyVacancies,
  listReceivedApplications,
  listVacancyTags,
  normalizeVacancyPayload,
  updateVacancy,
} from './vacancyManagementService';

jest.mock('../api/apiClient', () => ({
  __esModule: true,
  default: {
    get: jest.fn(), post: jest.fn(), put: jest.fn(), patch: jest.fn(), delete: jest.fn(),
  },
}));

beforeEach(() => {
  Object.values(apiClient).forEach((method) => method.mockReset());
});

const valid = {
  titulo: '  Cantora  ', descricao: '  Show  ', requisitos: '  Portfólio  ',
  areaId: 6, valorMinimo: '150.50', valorMaximo: '300', formaRemuneracao: 'POR_EVENTO', cidade: ' Recife ', estado: 'pe',
  modeloTrabalho: 'PRESENCIAL', tipoContrato: ' Cachê ', funcaoIds: ['3', 3, 7, -1],
  enderecoCompleto: ' ', beneficios: ' Transporte ', categoria: '', experiencia: ' Plena ',
  dataLimiteCandidatura: '', abrangencia: ' regional ', fotos: [' a.jpg ', '', 'b.jpg'],
  contratanteId: 999, usuarioId: 888, id: 77, status: 'CANCELADA', dataPublicacao: '2000-01-01',
};

test('normaliza somente campos editáveis e remove identidade/status/dados controlados', () => {
  const payload = normalizeVacancyPayload(valid);
  expect(payload).toMatchObject({ titulo: 'Cantora', valorMinimo: 150.5, valorMaximo: 300, areaId: 6, formaRemuneracao: 'POR_EVENTO', estado: 'PE', funcaoIds: [3, 7], enderecoCompleto: null, fotos: ['a.jpg', 'b.jpg'] });
  expect(payload).not.toHaveProperty('contratanteId');
  expect(payload).not.toHaveProperty('usuarioId');
  expect(payload).not.toHaveProperty('id');
  expect(payload).not.toHaveProperty('status');
  expect(payload).not.toHaveProperty('dataPublicacao');
});

test('lista minhas vagas com paginação limitada e cursor opcional', async () => {
  apiClient.get.mockResolvedValue({ content: [] });
  await listMyVacancies();
  await listMyVacancies({ cursor: 20, size: 20 });
  expect(apiClient.get).toHaveBeenNthCalledWith(1, '/vagas/minhas?size=20');
  expect(apiClient.get).toHaveBeenNthCalledWith(2, '/vagas/minhas?size=20&cursor=20');
});

test('consulta tags e candidaturas recebidas pelos endpoints reais', async () => {
  apiClient.get.mockResolvedValue([]);
  await listVacancyTags();
  await listReceivedApplications();
  expect(apiClient.get).toHaveBeenNthCalledWith(1, '/funcoes');
  expect(apiClient.get).toHaveBeenNthCalledWith(2, '/candidaturas/minhas-vagas?page=0&size=50');
});

test('pagina todas as candidaturas recebidas para manter filtros e contagens exatos', async () => {
  apiClient.get
    .mockResolvedValueOnce(Array.from({ length: 50 }, (_, index) => ({ id: index + 1 })))
    .mockResolvedValueOnce([{ id: 51 }]);
  await expect(listAllReceivedApplications()).resolves.toHaveLength(51);
  expect(apiClient.get).toHaveBeenNthCalledWith(1, '/candidaturas/minhas-vagas?page=0&size=50');
  expect(apiClient.get).toHaveBeenNthCalledWith(2, '/candidaturas/minhas-vagas?page=1&size=50');
});

test('publica sem identidade e sem status no payload', async () => {
  apiClient.post.mockResolvedValue({ id: 1 });
  await createVacancy(valid);
  expect(apiClient.post).toHaveBeenCalledWith('/vagas', expect.not.objectContaining({ contratanteId: expect.anything(), usuarioId: expect.anything(), status: expect.anything() }));
});

test('edita somente detalhes autorizados', async () => {
  apiClient.put.mockResolvedValue({ id: 4 });
  await updateVacancy(4, valid);
  expect(apiClient.put).toHaveBeenCalledWith('/vagas/4', expect.objectContaining({ titulo: 'Cantora', funcaoIds: [3, 7] }));
  expect(apiClient.put.mock.calls[0][1]).not.toHaveProperty('status');
});

test('consulta detalhe por ID sem enviar identidade', async () => {
  apiClient.get.mockResolvedValue({ id: 12 });
  await getManagedVacancy(12);
  expect(apiClient.get).toHaveBeenCalledWith('/vagas/12');
});

test('envia somente a ação no PATCH de status', async () => {
  apiClient.patch.mockResolvedValue({ status: 'PAUSADA' });
  await changeVacancyStatus(9, 'SUSPENDER');
  expect(apiClient.patch).toHaveBeenCalledWith('/vagas/9/status', { acao: 'SUSPENDER' });
});

test('cancela via DELETE com confirmação verdadeira e motivo normalizado', async () => {
  apiClient.delete.mockResolvedValue(null);
  await cancelVacancy(9, '  Projeto adiado  ');
  expect(apiClient.delete).toHaveBeenCalledWith('/vagas/9', { body: { confirmacao: true, motivo: 'Projeto adiado' } });
});
