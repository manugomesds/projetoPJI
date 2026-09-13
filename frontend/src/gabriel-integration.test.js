import fs from 'fs';
import path from 'path';
import { waitFor } from '@testing-library/dom';
import { getSession, saveSession } from './auth/sessionService';
import { normalizeVacancyPayload } from './services/vagas/vacancyManagementService';
import { vacancyRemuneration } from './components/vagas/vacancyPresentation';

const read = (name) => fs.readFileSync(path.join(process.cwd(), 'public', name), 'utf8');
const script = read('js/main.js');
const session = { token: 'jwt-test', id: 9, tipoUsuario: 'ARTISTA', perfilCompleto: true, statusConta: 'ATIVA' };
const json = (data, status = 200) => ({ ok: status < 400, status, headers: { get: () => 'application/json' }, json: async () => data });
let ready;
let addEvent;
function mount(name, query = '', account = session) {
  window.history.replaceState({}, '', `/${name}${query}`);
  document.body.innerHTML = read(name).replace(/[\s\S]*<body[^>]*>/, '').replace(/<script[\s\S]*/, '');
  if (account) sessionStorage.setItem('palco.sessao', JSON.stringify(account));
  window.eval(script);
  ready();
}

beforeEach(() => {
  sessionStorage.clear(); localStorage.clear();
  window.fetch = jest.fn(); window.confirm = jest.fn(() => true);
  addEvent = document.addEventListener.bind(document);
  jest.spyOn(document, 'addEventListener').mockImplementation((type, fn, options) => {
    if (type === 'DOMContentLoaded') { ready = fn; return; }
    return addEvent(type, fn, options);
  });
});
afterEach(() => jest.restoreAllMocks());

test.each([
  { status: 'AGUARDANDO_DADOS', statusConta: 'PENDENTE_TIPO_PERFIL' },
  { status: 'AGUARDANDO_DADOS' },
  { status: 'AUTENTICADO', statusConta: 'PENDENTE_EMAIL', token: 'must-not-save' },
])('Google pendente não salva sessão nem abre painel: %j', async (response) => {
  mount('google-callback.html');
  window.eval(read('js/fluxo-conta.js'));
  window.fetch.mockResolvedValue(json(response));
  await window.receberCredencialPalcoGoogle({ credential: 'provider-id-token' });
  expect(window.fetch).toHaveBeenCalledWith('http://localhost:8080/api/auth/google', expect.objectContaining({
    method: 'POST', body: JSON.stringify({ idToken: 'provider-id-token' }),
  }));
  expect(getSession()).toBeNull();
  expect(localStorage.getItem('palco.sessao')).toBeNull();
  expect(document.querySelector('[data-google-tipo]')).not.toHaveAttribute('hidden');
  expect(document.querySelector('[data-google-tipo]')).toHaveTextContent('Nenhuma sessão autenticada foi iniciada');
  expect(window.location.pathname).toBe('/google-callback.html');
});

test('Google AUTENTICADO com conta ativa e token permite criar sessão', async () => {
  mount('google-callback.html', '', null);
  window.fetch.mockResolvedValue(json({ ...session, status: 'AUTENTICADO' }));
  await window.PalcoGoogle.entrar('provider-id-token');
  expect(getSession()).toMatchObject(session);
});

test('Google AUTENTICADO sem JWT não cria sessão', async () => {
  mount('google-callback.html', '', null);
  window.fetch.mockResolvedValue(json({ status: 'AUTENTICADO', statusConta: 'ATIVA' }));
  await expect(window.PalcoGoogle.entrar('provider-id-token')).rejects.toThrow('inválida');
  expect(getSession()).toBeNull();
});

test('guard React recusa pendência mesmo com token e limpa sessão anterior', () => {
  saveSession(session);
  expect(() => saveSession({ ...session, status: 'AGUARDANDO_DADOS' })).toThrow('pendente');
  expect(getSession()).toBeNull();
});

test('retirada aceita resposta 204 sem tentar ler JSON vazio', async () => {
  window.fetch.mockResolvedValueOnce(json([{ id: 11, vagaId: 7, status: 'PENDENTE' }]));
  mount('minhas-candidaturas.html');
  await waitFor(() => expect(document.querySelector('[data-retirar-candidatura="11"]')).not.toBeNull());
  const parseEmpty = jest.fn().mockRejectedValue(new SyntaxError('Empty body'));
  window.fetch.mockResolvedValueOnce({ ok: true, status: 204, headers: { get: () => 'application/json' }, json: parseEmpty });
  document.querySelector('[data-retirar-candidatura="11"]').click();
  await waitFor(() => expect(document.querySelector('[data-candidatura-id="11"] .status')).toHaveTextContent('Retirada'));
  expect(parseEmpty).not.toHaveBeenCalled();
});

test.each(['verificar-email.html', 'responsavel.html', 'google-callback.html'])('query string não confirma conta em %s', (name) => {
  mount(name, '?estado=sucesso&token=untrusted&status=AUTENTICADO', null);
  window.eval(read('js/fluxo-conta.js'));
  expect(window.fetch).not.toHaveBeenCalled();
  expect(getSession()).toBeNull();
  const visible = [...document.querySelectorAll('main section')].filter(el => !el.hidden).map(el => el.textContent).join(' ');
  expect(visible).not.toMatch(/Sua conta está ativa|Conta\s+liberada|E-mail\s+confirmado/);
});

test.each([403, 422])('exclusão HTTP %s mantém conta e informa bloqueio', async (status) => {
  mount('excluir-conta.html');
  window.fetch.mockResolvedValue(json({ mensagem: 'Operação indisponível.' }, status));
  const confirmation = document.getElementById('exclusao-confirmacao');
  confirmation.value = 'EXCLUIR'; confirmation.dispatchEvent(new Event('input'));
  document.querySelector('[data-exclusao-confirmar]').click();
  await waitFor(() => expect(document.querySelector('[data-exclusao-erro]')).not.toHaveAttribute('hidden'));
  expect(document.querySelector('[data-exclusao-erro]')).toHaveTextContent('Sua conta foi mantida');
  expect(document.querySelector('[data-exclusao-sucesso]')).toHaveAttribute('hidden');
  expect(getSession()).toMatchObject(session);
  expect(window.fetch).toHaveBeenCalledTimes(1);
  expect(window.fetch.mock.calls[0][0]).toBe('http://localhost:8080/api/usuarios/me');
});

test.each(['ACEITA', 'REJEITADA'])('análise transmite %s e apresenta o retorno do backend', async (status) => {
  const application = { candidaturaId: 12, artistaId: 3, nomeArtista: 'Ana', status: 'PENDENTE', linkPortfolioCandidatura: 'javascript:alert(1)', mensagemApresentacao: 'Olá' };
  window.fetch.mockImplementation((url, config) => {
    if (config.method === 'PUT') return Promise.resolve(json({ status }));
    if (url.includes('/candidaturas?')) return Promise.resolve(json({ content: [application], hasNext: false, totalElements: 1 }));
    return Promise.resolve(json({ id: 4, titulo: 'Evento' }));
  });
  mount('candidatos-vaga.html', '?vaga=4', { ...session, tipoUsuario: 'CONTRATANTE' });
  await waitFor(() => expect(document.querySelector(`[data-destino="${status}"]`)).not.toBeNull());
  expect(document.querySelector('a[href^="javascript:"]')).toBeNull();
  document.querySelector(`[data-destino="${status}"]`).click();
  await waitFor(() => expect(document.querySelector('[data-analisar]')).toBeNull());
  const [url, config] = window.fetch.mock.calls.find(([, cfg]) => cfg.method === 'PUT');
  expect(url).toBe('http://localhost:8080/api/candidaturas/12');
  expect(JSON.parse(config.body)).toMatchObject({ status, vagaId: 4, artistaId: 3 });
});

test('payload de vaga não reinterpreta IDs nem campos legados', () => {
  const payload = normalizeVacancyPayload({ areaId: 8, funcaoIds: [27], tagIds: [99], categoria: 'Livre', formaPagamento: 'Pix', remuneraValor: 400, formaRemuneracao: 'POR_EVENTO', valorMinimo: 150, valorMaximo: 300, abrangencia: 'LOCAL' });
  expect(payload).toMatchObject({ areaId: 8, funcaoIds: [27], valorMinimo: 150, valorMaximo: 300, formaRemuneracao: 'POR_EVENTO', abrangencia: 'LOCAL' });
  ['tagIds', 'categoria', 'formaPagamento', 'remuneraValor'].forEach(field => expect(payload).not.toHaveProperty(field));
  expect(vacancyRemuneration(payload)).toMatch(/150.*300.*Por Evento/);
});

test('telas novas possuem cabeçalho fechado e destinos estáticos existentes', () => {
  const pages = ['candidatos-vaga.html', 'minhas-candidaturas.html', 'notificacoes.html', 'excluir-conta.html', 'google-callback.html', 'responsavel.html', 'verificar-email.html'];
  for (const name of pages) {
    const html = read(name);
    expect(html.indexOf('</header>')).toBeGreaterThan(0);
    expect(html.indexOf('</header>')).toBeLessThan(html.indexOf('<main'));
    const doc = new DOMParser().parseFromString(html, 'text/html');
    expect(doc.querySelector('header main')).toBeNull();
    for (const link of doc.querySelectorAll('a[href]')) {
      const value = link.getAttribute('href').split(/[?#]/)[0];
      if (value.endsWith('.html')) expect(fs.existsSync(path.join(process.cwd(), 'public', value))).toBe(true);
    }
  }
});

test('nenhum consumidor produtivo chama o catálogo de tags descontinuado', () => {
  function walk(dir) {
    return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)]);
  }
  const files = ['src', 'public'].flatMap(dir => walk(path.join(process.cwd(), dir))).filter(file => /\.(js|jsx|html)$/.test(file) && !/\.test\./.test(file));
  for (const file of files) expect(fs.readFileSync(file, 'utf8')).not.toMatch(/['"`]\/(?:api\/)?tags(?:['"`?\/])/);
});
