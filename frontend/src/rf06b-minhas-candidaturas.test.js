import { waitFor } from '@testing-library/dom';
import fs from 'fs';
import path from 'path';

const script = fs.readFileSync(
  path.join(process.cwd(), 'public', 'js', 'main.js'),
  'utf8'
);

const pagina = fs.readFileSync(
  path.join(process.cwd(), 'public', 'minhas-candidaturas.html'),
  'utf8'
);
const corpo = pagina
  .replace(/[\s\S]*<body[^>]*>/, '')
  .replace(/<script[\s\S]*/, '');

// main.js registra um listener de DOMContentLoaded no document, que o jsdom
// compartilha entre os testes do arquivo. Capturamos o listener desta instancia
// em vez de registra-lo, para o teste N nao disparar N cadeias de inicializacao.
const registrarOriginal = document.addEventListener.bind(document);
let iniciarEstaInstancia = null;

function montar(sessao) {
  iniciarEstaInstancia = null;
  document.addEventListener = (tipo, ouvinte, opcoes) => {
    if (tipo === 'DOMContentLoaded') {
      iniciarEstaInstancia = ouvinte;
      return undefined;
    }
    return registrarOriginal(tipo, ouvinte, opcoes);
  };
  document.body.innerHTML = corpo;
  window.history.replaceState({}, '', '/minhas-candidaturas.html');
  sessionStorage.clear();
  localStorage.clear();
  if (sessao) sessionStorage.setItem('palco.sessao', JSON.stringify(sessao));
}

function respostaJson(corpoResposta, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => 'application/json' },
    json: async () => (Array.isArray(corpoResposta)
      ? corpoResposta.map((item) => ({ ...item }))
      : { ...corpoResposta })
  };
}

const ARTISTA = { token: 'jwt', id: 9, tipoUsuario: 'ARTISTA', perfilCompleto: true };

function candidatura(id, status = 'PENDENTE') {
  return {
    id,
    vagaId: 100 + id,
    artistaId: 9,
    mensagemApresentacao: 'Apresentação ' + id,
    linkPortfolioCandidatura: 'https://exemplo.com/' + id,
    status,
    dataCandidatura: '2026-09-01T10:00:00'
  };
}

const linhas = () => document.querySelectorAll('.candidatura-linha');
const botaoMais = () => document.querySelector('[data-candidaturas-mais]');

function iniciar() {
  window.eval(script);
  iniciarEstaInstancia();
}

test('artista sem candidaturas vê o estado vazio e um caminho para buscar vagas', async () => {
  montar(ARTISTA);
  window.fetch = jest.fn().mockResolvedValue(respostaJson([]));
  iniciar();

  await waitFor(() =>
    expect(document.querySelector('[data-candidaturas-vazio]')).not.toHaveAttribute('hidden')
  );
  expect(linhas().length).toBe(0);
  expect(document.querySelector('[data-candidaturas-vazio] a').getAttribute('href'))
    .toBe('buscar-vagas.html');
});

test('primeira página pede page=0 e size=20, como o contrato do servidor', async () => {
  montar(ARTISTA);
  const fetchMock = jest.fn().mockResolvedValue(respostaJson([candidatura(1)]));
  window.fetch = fetchMock;
  iniciar();

  await waitFor(() => expect(linhas().length).toBe(1));
  expect(fetchMock.mock.calls[0][0])
    .toBe('http://localhost:8080/api/candidaturas?page=0&size=20');
});

test('página cheia oferece carregar mais; página curta encerra o histórico', async () => {
  montar(ARTISTA);
  const cheia = Array.from({ length: 20 }, (unused, i) => candidatura(i + 1));
  const fetchMock = jest.fn()
    .mockResolvedValueOnce(respostaJson(cheia))
    .mockResolvedValueOnce(respostaJson([candidatura(21)]));
  window.fetch = fetchMock;
  iniciar();

  await waitFor(() => expect(linhas().length).toBe(20));
  expect(botaoMais()).not.toHaveAttribute('hidden');
  expect(document.querySelector('[data-candidaturas-fim]')).toHaveAttribute('hidden');

  botaoMais().click();

  await waitFor(() => expect(linhas().length).toBe(21));
  expect(fetchMock.mock.calls[1][0])
    .toBe('http://localhost:8080/api/candidaturas?page=1&size=20');
  expect(botaoMais()).toHaveAttribute('hidden');
  expect(document.querySelector('[data-candidaturas-fim]')).not.toHaveAttribute('hidden');
});

test('retirada é oferecida em PENDENTE, EM_ANALISE e REJEITADA', async () => {
  montar(ARTISTA);
  window.fetch = jest.fn().mockResolvedValue(respostaJson([
    candidatura(1, 'PENDENTE'),
    candidatura(2, 'EM_ANALISE'),
    candidatura(3, 'ACEITA'),
    candidatura(4, 'REJEITADA'),
    candidatura(5, 'RETIRADA'),
    candidatura(6, 'CANCELADA_POR_VAGA')
  ]));
  iniciar();

  await waitFor(() => expect(linhas().length).toBe(6));
  expect(document.querySelectorAll('[data-retirar-candidatura]').length).toBe(3);
  expect(document.querySelector('[data-candidatura-id="1"] [data-retirar-candidatura]')).toBeTruthy();
  expect(document.querySelector('[data-candidatura-id="3"] [data-retirar-candidatura]')).toBeFalsy();
});

test('retirar chama DELETE e a linha passa a mostrar Retirada', async () => {
  montar(ARTISTA);
  const fetchMock = jest.fn().mockResolvedValue(respostaJson([candidatura(1, 'PENDENTE')]));
  window.fetch = fetchMock;
  iniciar();

  await waitFor(() => expect(linhas().length).toBe(1));

  window.confirm = jest.fn(() => true);
  fetchMock.mockResolvedValueOnce({
    ok: true, status: 204, headers: { get: () => null }, json: async () => null
  });
  document.querySelector('[data-retirar-candidatura]').click();

  await waitFor(() =>
    expect(document.querySelector('.candidatura-linha .status')).toHaveTextContent('Retirada')
  );
  const [url, config] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1];
  expect(url).toBe('http://localhost:8080/api/candidaturas/1');
  expect(config.method).toBe('DELETE');
  expect(document.querySelector('[data-retirar-candidatura]')).toBeFalsy();
});

test('recusa no confirm não chama a API', async () => {
  montar(ARTISTA);
  const fetchMock = jest.fn().mockResolvedValue(respostaJson([candidatura(1, 'PENDENTE')]));
  window.fetch = fetchMock;
  iniciar();

  await waitFor(() => expect(linhas().length).toBe(1));
  window.confirm = jest.fn(() => false);
  const antes = fetchMock.mock.calls.length;
  document.querySelector('[data-retirar-candidatura]').click();

  expect(fetchMock.mock.calls.length).toBe(antes);
  expect(document.querySelector('.candidatura-linha .status'))
    .toHaveTextContent('Candidatura enviada');
});

test('contratante é mandado para as vagas e a listagem nem é chamada', async () => {
  montar({ token: 'jwt', id: 1, tipoUsuario: 'CONTRATANTE', perfilCompleto: true });
  const fetchMock = jest.fn().mockResolvedValue(respostaJson([]));
  window.fetch = fetchMock;
  iniciar();

  await waitFor(() =>
    expect(document.querySelector('[data-candidaturas-contratante]')).not.toHaveAttribute('hidden')
  );
  const chamouCandidaturas = fetchMock.mock.calls
    .some(([url]) => String(url).includes('/candidaturas'));
  expect(chamouCandidaturas).toBe(false);
});

test('falha de rede mostra mensagem em português e permite tentar de novo', async () => {
  montar(ARTISTA);
  const fetchMock = jest.fn().mockRejectedValueOnce(new TypeError('Failed to fetch'));
  window.fetch = fetchMock;
  iniciar();

  await waitFor(() =>
    expect(document.querySelector('[data-candidaturas-erro]')).not.toHaveAttribute('hidden')
  );
  expect(document.querySelector('[data-candidaturas-erro-texto]').textContent)
    .toMatch(/verifique sua conexão/i);

  fetchMock.mockResolvedValueOnce(respostaJson([candidatura(1)]));
  document.querySelector('[data-candidaturas-tentar]').click();

  await waitFor(() => expect(linhas().length).toBe(1));
  expect(document.querySelector('[data-candidaturas-erro]')).toHaveAttribute('hidden');
});

test('a mensagem de apresentação é escapada, não interpretada como HTML', async () => {
  montar(ARTISTA);
  const perigosa = candidatura(1);
  perigosa.mensagemApresentacao = '<img src=x onerror="window.__xss=1">';
  window.fetch = jest.fn().mockResolvedValue(respostaJson([perigosa]));
  iniciar();

  await waitFor(() => expect(linhas().length).toBe(1));
  expect(document.querySelector('.candidatura-linha__mensagem img')).toBeNull();
  expect(document.querySelector('.candidatura-linha__mensagem').textContent)
    .toContain('<img src=x');
  expect(window.__xss).toBeUndefined();
});
