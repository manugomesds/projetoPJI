import apiClient from '../api/apiClient';

export function getRegistrationAreas(options = {}) {
  return apiClient.get('/areas', { ...options, token: null });
}
