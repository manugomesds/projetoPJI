import apiClient from '../api/apiClient';

const FILTERS = ['areaId', 'funcaoIds', 'especializacaoIds', 'localizacao', 'raios',
  'experienciaMinima', 'disponivel', 'tipos', 'vagaId', 'recomendados', 'ordenacao', 'page', 'size'];

export function talentQuery(values = {}) {
  const query = new URLSearchParams();
  FILTERS.forEach((key) => {
    const value = values[key];
    if (value === undefined || value === null || value === '' || (Array.isArray(value) && !value.length)) return;
    query.set(key, Array.isArray(value) ? value.join(',') : String(value));
  });
  return query.toString();
}

export function getTalents(filters, options = {}) {
  return apiClient.get('/talentos?' + talentQuery({ page: 0, size: 20, ...filters }), options);
}

export function getTalentCatalog(resource, filters, options = {}) {
  if (!['areas', 'funcoes', 'especializacoes', 'contextos'].includes(resource)) throw new Error('Catálogo inválido.');
  return apiClient.get('/talentos/' + resource + '?' + talentQuery({ page: 0, size: 20, ...filters }), options);
}

export function talentError(error) {
  if (error.status === 401) return 'Sua sessão expirou. Entre novamente para consultar talentos.';
  if (error.status === 403) return 'Acesso negado. Banco de Talentos exclusivo para contratantes com conta ativa.';
  if (error.status === 404) return 'A vaga de contexto não está disponível para sua conta.';
  if (error.status === 400 || error.status === 422) return error.message || 'Revise os filtros selecionados.';
  return 'Não foi possível carregar os talentos. Tente novamente.';
}
