import apiClient from '../api/apiClient';
import { getRegistrationAreas } from './areaService';
jest.mock('../api/apiClient', () => ({ get: jest.fn() }));
test('consulta somente o catálogo público, sem token de uma sessão anterior', async () => {
  const signal = new AbortController().signal;
  apiClient.get.mockResolvedValue([{ id: 47, nome: 'Música' }]);
  expect(await getRegistrationAreas({ signal })).toEqual([{ id: 47, nome: 'Música' }]);
  expect(apiClient.get).toHaveBeenCalledWith('/areas', { signal, token: null });
});
