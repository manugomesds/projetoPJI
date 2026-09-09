import { SESSION_STORAGE_KEY } from '../../auth/sessionService';
import ApiError from '../api/ApiError';
import apiClient from '../api/apiClient';
import { buildVacancyParams, getSimilarVacancies, getVacancyPage } from './vacancyListingService';

jest.mock('../api/apiClient', () => ({
  __esModule: true,
  default: { get: jest.fn() },
}));

beforeEach(() => {
  apiClient.get.mockReset();
  window.localStorage.clear();
  window.sessionStorage.clear();
});

test('serializa somente filtros preenchidos e os dois cursores', () => {
  const params = buildVacancyParams({
    titulo: '  cantora  ',
    cidade: '',
    estado: 'SP',
    tagIds: [4, '', 9],
  }, { cursor: 25, cursorCanceladas: 7, size: 99 });

  expect(params.get('titulo')).toBe('cantora');
  expect(params.has('cidade')).toBe(false);
  expect(params.get('estado')).toBe('SP');
  expect(params.getAll('tagIds')).toEqual(['4', '9']);
  expect(params.get('cursor')).toBe('25');
  expect(params.get('cursorCanceladas')).toBe('7');
  expect(params.get('size')).toBe('50');
});

test('usa size 20 e endpoint público quando não há sessão', async () => {
  apiClient.get.mockResolvedValue({ content: [] });

  await getVacancyPage();

  expect(apiClient.get).toHaveBeenCalledWith('/vagas?size=20');
});

test('preserva JWT opcional e refaz como anônimo se a sessão estiver expirada', async () => {
  window.sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ token: 'jwt-expirado' }));
  apiClient.get
    .mockRejectedValueOnce(new ApiError({ status: 401, message: 'Expirado' }))
    .mockResolvedValueOnce({ content: [] });

  await getVacancyPage({ filters: { cidade: 'Recife' }, size: 20 });

  expect(apiClient.get).toHaveBeenNthCalledWith(1, '/vagas?cidade=Recife&size=20');
  expect(apiClient.get).toHaveBeenNthCalledWith(
    2,
    '/vagas?cidade=Recife&size=20',
    { token: null }
  );
});

test('consulta similares pelo endpoint público existente com limite normalizado', async () => {
  apiClient.get.mockResolvedValue({ content: [] });

  await getSimilarVacancies(17, { size: 99 });

  expect(apiClient.get).toHaveBeenCalledWith('/vagas/17/similares?size=50');
});
