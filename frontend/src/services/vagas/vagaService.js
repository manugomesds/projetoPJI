import apiClient from '../api/apiClient';

const requestsInFlight = new Map();

export function getVagaDetails(id) {
  const key = String(id);

  if (!requestsInFlight.has(key)) {
    const request = apiClient
      .get(`/vagas/${encodeURIComponent(key)}`)
      .finally(() => requestsInFlight.delete(key));
    requestsInFlight.set(key, request);
  }

  return requestsInFlight.get(key);
}

export function getSuggestedArtists(id, { page = 0, size = 3 } = {}) {
  const requestedPage = Number(page);
  const requestedSize = Number(size);
  const normalizedPage = Number.isFinite(requestedPage)
    ? Math.max(0, Math.trunc(requestedPage))
    : 0;
  const normalizedSize = Number.isFinite(requestedSize)
    ? Math.min(50, Math.max(1, Math.trunc(requestedSize)))
    : 3;
  const params = new URLSearchParams({
    page: String(normalizedPage),
    size: String(normalizedSize),
  });

  return apiClient.get(`/vagas/${encodeURIComponent(id)}/candidaturas?${params.toString()}`);
}
