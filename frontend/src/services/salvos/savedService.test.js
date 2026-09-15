import apiClient from '../api/apiClient';
import { getSavedState, getSavedItems, saveItem, removeSavedItem } from './savedService';
jest.mock('../api/apiClient', () => ({ get: jest.fn(), post: jest.fn(), delete: jest.fn() }));
beforeEach(() => { jest.clearAllMocks(); sessionStorage.clear(); localStorage.clear(); });
test('contrato nunca envia dono e remove por tipo/alvo', async () => {
  await saveItem('VAGA', '91'); await removeSavedItem('VAGA', 91);
  expect(apiClient.post).toHaveBeenCalledWith('/salvos', { tipoAlvo: 'VAGA', alvoId: 91 });
  expect(apiClient.delete).toHaveBeenCalledWith('/salvos/VAGA/91');
});
test('lista tem paginação e filtro codificados', async () => {
  await getSavedItems({ tipoAlvo: 'PERFIL_ARTISTA', page: 1 });
  expect(apiClient.get).toHaveBeenCalledWith('/salvos?page=1&size=20&tipoAlvo=PERFIL_ARTISTA', { cache: 'no-store' });
});
test('deduplica consultas simultâneas sem manter estado local após reload', async () => {
  apiClient.get.mockResolvedValue({ salvo: true });
  await Promise.all([getSavedState('VAGA',91), getSavedState('VAGA',91)]);
  expect(apiClient.get).toHaveBeenCalledTimes(1);
  await getSavedState('VAGA',91); expect(apiClient.get).toHaveBeenCalledTimes(2);
});
