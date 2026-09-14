import sessionService from '../../auth/sessionService';
import { API_BASE_URL } from '../../config/apiConfig';
import ApiError from './ApiError';

function buildUrl(path) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${API_BASE_URL}${normalizedPath}`;
}

async function parseResponse(response) {
  if (response.status === 204) return null;

  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return response.json();
  }

  const text = await response.text();
  return text || null;
}

function errorMessage(body, status) {
  if (body && typeof body === 'object') {
    return body.mensagem || body.message || `Falha na API (HTTP ${status}).`;
  }
  return typeof body === 'string' && body ? body : `Falha na API (HTTP ${status}).`;
}

async function request(path, options = {}) {
  const { body, headers: customHeaders, token, responseType, ...fetchOptions } = options;
  const headers = new Headers({ Accept: 'application/json', ...customHeaders });
  const accessToken = token === undefined ? sessionService.getAccessToken() : token;
  let requestBody = body;

  if (typeof FormData !== 'undefined' && body instanceof FormData) {
    headers.delete('Content-Type'); // O navegador gera o boundary multipart.
  } else if (body !== undefined && body !== null && typeof body !== 'string') {
    headers.set('Content-Type', 'application/json');
    requestBody = JSON.stringify(body);
  }

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  const response = await fetch(buildUrl(path), {
    ...fetchOptions,
    headers,
    body: requestBody,
  });
  if (response.ok && responseType === 'stream') return response;
  if (response.ok && responseType === 'blob') return response.blob();
  const responseBody = await parseResponse(response);

  if (!response.ok) {
    throw new ApiError({
      status: response.status,
      message: errorMessage(responseBody, response.status),
      body: responseBody,
    });
  }

  return responseBody;
}

const apiClient = {
  getStream(path, options = {}) {
    return request(path, {
      ...options,
      method: 'GET',
      responseType: 'stream',
      headers: { ...options.headers, Accept: 'text/event-stream' },
    });
  },
  get(path, options = {}) {
    return request(path, { ...options, method: 'GET' });
  },
  post(path, body, options = {}) {
    return request(path, { ...options, method: 'POST', body });
  },
  put(path, body, options = {}) {
    return request(path, { ...options, method: 'PUT', body });
  },
  patch(path, body, options = {}) {
    return request(path, { ...options, method: 'PATCH', body });
  },
  delete(path, options = {}) {
    return request(path, { ...options, method: 'DELETE' });
  },
};

export { buildUrl, request };
export default apiClient;
