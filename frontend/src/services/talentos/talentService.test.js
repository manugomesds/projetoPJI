import apiClient from '../api/apiClient';
import { getTalents, getTalentCatalog, talentQuery, talentError } from './talentService';

jest.mock('../api/apiClient', () => ({ get: jest.fn() }));
beforeEach(() => jest.clearAllMocks());

test('serializa filtros OR, boolean false e texto sem permitir identidade do cliente', () => {
  const params = new URLSearchParams(talentQuery({ areaId: 2, funcaoIds: [12, 15], especializacaoIds: [],
    raios: ['REMOTO', 'LOCAL'], tipos: ['BANDA', 'DUPLA'], disponivel: false, localizacao: 'São Paulo & região',
    contratanteId: 999, usuarioId: 8 }));
  expect(params.get('funcaoIds')).toBe('12,15');
  expect(params.get('disponivel')).toBe('false');
  expect(params.get('localizacao')).toBe('São Paulo & região');
  expect(params.has('especializacaoIds')).toBe(false);
  expect(params.has('contratanteId')).toBe(false);
  expect(params.has('usuarioId')).toBe(false);
});
test('requisições usam apiClient e paginação padrão', () => {
  getTalents({ recomendados: true });
  expect(apiClient.get).toHaveBeenCalledWith('/talentos?recomendados=true&page=0&size=20', {});
});
test('catálogo transmite área/função/página e cancelamento', () => {
  const controller = new AbortController();
  getTalentCatalog('especializacoes', { areaId: 1, funcaoIds: [2, 3], page: 1 }, { signal: controller.signal });
  expect(apiClient.get).toHaveBeenCalledWith('/talentos/especializacoes?areaId=1&funcaoIds=2%2C3&page=1&size=20', { signal: controller.signal });
  expect(() => getTalentCatalog('usuarios')).toThrow('Catálogo inválido.');
});
test('erro interno não vaza SQL; validações reais preservam mensagem', () => {
  expect(talentError({ status: 500, message: 'SELECT senha' })).not.toContain('SELECT');
  expect(talentError({ status: 422, message: 'Função incompatível.' })).toBe('Função incompatível.');
});
