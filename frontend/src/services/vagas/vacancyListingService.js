import sessionService from '../../auth/sessionService';
import ApiError from '../api/ApiError';
import apiClient from '../api/apiClient';

export const VACANCY_FILTER_FIELDS = [
  'titulo',
  'empresa',
  'cidade',
  'estado',
  'modeloTrabalho',
  'tipoContrato',
  'faixaSalarialMin',
  'faixaSalarialMax',
  'areaAtuacao',
];

const requestsInFlight = new Map();

function hasValue(value) {
  return value !== null && value !== undefined && String(value).trim() !== '';
}

export function buildVacancyParams(filters = {}, pagination = {}) {
  const params = new URLSearchParams();

  VACANCY_FILTER_FIELDS.forEach((field) => {
    if (hasValue(filters[field])) params.set(field, String(filters[field]).trim());
  });

  if (Array.isArray(filters.tagIds)) {
    filters.tagIds.filter(hasValue).forEach((tagId) => params.append('tagIds', String(tagId)));
  }
  if (hasValue(pagination.cursor)) params.set('cursor', String(pagination.cursor));
  if (hasValue(pagination.cursorCanceladas)) {
    params.set('cursorCanceladas', String(pagination.cursorCanceladas));
  }

  const requestedSize = Number(pagination.size);
  const size = Number.isFinite(requestedSize)
    ? Math.min(50, Math.max(1, Math.trunc(requestedSize)))
    : 20;
  params.set('size', String(size));
  return params;
}

async function requestPublicFeed(path) {
  const hadSession = Boolean(sessionService.getAccessToken());

  try {
    return await apiClient.get(path);
  } catch (error) {
    if (!(hadSession && error instanceof ApiError && error.status === 401)) throw error;
    return apiClient.get(path, { token: null });
  }
}

export function getVacancyPage({ filters = {}, cursor, cursorCanceladas, size = 20 } = {}) {
  const params = buildVacancyParams(filters, { cursor, cursorCanceladas, size });
  const path = `/vagas?${params.toString()}`;
  const key = `${sessionService.isAuthenticated() ? 'auth' : 'public'}:${path}`;

  if (!requestsInFlight.has(key)) {
    const request = requestPublicFeed(path).finally(() => requestsInFlight.delete(key));
    requestsInFlight.set(key, request);
  }

  return requestsInFlight.get(key);
}

export function getSimilarVacancies(vacancyId, { size = 3 } = {}) {
  const requestedSize = Number(size);
  const normalizedSize = Number.isFinite(requestedSize)
    ? Math.min(50, Math.max(1, Math.trunc(requestedSize)))
    : 3;
  const path = `/vagas/${encodeURIComponent(vacancyId)}/similares?size=${normalizedSize}`;
  return requestPublicFeed(path);
}
