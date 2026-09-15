import apiClient from '../api/apiClient';
import sessionService from '../../auth/sessionService';

export const SAVED_EVENT = 'palco:salvo-alterado';
export const savedKey = (tipoAlvo, alvoId) => `${tipoAlvo}/${alvoId}`;
const inFlight = new Map();
export function getSavedState(tipoAlvo, alvoId) {
  const query = new URLSearchParams({ tipoAlvo, alvoId: String(alvoId) });
  const key = `${sessionService.getAccessToken()}:${query}`;
  if (!inFlight.has(key)) {
    inFlight.set(key, Promise.resolve(apiClient.get(`/salvos/estado?${query}`, { cache: 'no-store' }))
      .then(data => {
        if (typeof data?.salvo !== 'boolean') throw new Error('Não foi possível consultar o estado do salvo.');
        return data;
      }).finally(() => inFlight.delete(key)));
  }
  return inFlight.get(key);
}
export function saveItem(tipoAlvo, alvoId) { return apiClient.post('/salvos', { tipoAlvo, alvoId: Number(alvoId) }); }
export function removeSavedItem(tipoAlvo, alvoId) {
  return apiClient.delete(`/salvos/${encodeURIComponent(tipoAlvo)}/${encodeURIComponent(alvoId)}`);
}
export function getSavedItems({ tipoAlvo, page = 0, size = 20 }, options = {}) {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (tipoAlvo) query.set('tipoAlvo', tipoAlvo);
  return apiClient.get(`/salvos?${query}`, { cache: 'no-store', ...options });
}
export function publishSavedState(tipoAlvo, alvoId, state) {
  window.dispatchEvent(new CustomEvent(SAVED_EVENT, { detail: { key: savedKey(tipoAlvo, alvoId), state } }));
}
