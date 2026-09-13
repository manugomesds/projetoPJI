import { waitFor } from '@testing-library/dom';
import fs from 'fs';
import path from 'path';

const script = fs.readFileSync(
  path.join(process.cwd(), 'public', 'js', 'main.js'),
  'utf8'
);

const pagina = fs.readFileSync(
  path.join(process.cwd(), 'public', 'notificacoes.html'),
  'utf8'
);
const corpo = pagina
  .replace(/[\s\S]*<body[^>]*>/, '')
  .replace(/<script[\s\S]*/, '');

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
  window.history.replaceState({}, '', '/notificacoes.html');
  sessionStorage.clear();
  localStorage.clear();
  if (sessao) sessionStorage.setItem('palco.sessao', JSON.stringify(sessao));
}

function respostaJson(corpoResposta, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => 'application/json' },
    json: async () => JSON.parse(JSON.stringify(corpoResposta))
  };
}

function paginaDe(itens, hasNext = false) {
  return {
    content: itens,
    page: 0,
    size: 20,
    totalElements: itens.length,
    totalPages: 1,
    first: true,
    last: !hasNext,
    hasNext,
    hasPrevious: false
  };
}

function notificacao(id, extra = {}) {
  return {
    id,
    tipo: 'CANDIDATURA',
    mensagem: 'Nova candidatura recebida',
    link: null,
    lida: false,
    data: '2026-09-10T09:00:00',
    ...extra
  };
}

const SESSAO = { token: 'jwt', id: 9, tipoUsuario: 'ARTISTA', perfilCompleto: true };
const itens = () => document.querySelectorAll('.central-item');
const naoLidas = () => document.querySelectorAll('.central-item--nao-lida');

function iniciar() {
  window.eval(script);
  iniciarEstaInstancia();
}

test('lista notificações pedindo page=0 e size=20', async () => {
  montar(SESSAO);
  const fetchMock = jest.fn().mockResolvedValue(respostaJson(paginaDe([notificacao(1)])));
  window.fetch = fetchMock;
  iniciar();

  await waitFor(() => expect(itens().length).toBe(1));
  const chamada = fetchMock.mock.calls.find(([url]) => String(url).includes('/notificacoes?'));
  expect(chamada[0]).toBe('http://localhost:8080/api/notificacoes?page=0&size=20');
});

test('sem notificações mostra o estado vazio', async () => {
  montar(SESSAO);
  window.fetch = jest.fn().mockResolvedValue(respostaJson(paginaDe([])));
  iniciar();

  await waitFor(() =>
    expect(document.querySelector('[data-notificacoes-vazio]')).not.toHaveAttribute('hidden')
  );
  expect(itens().length).toBe(0);
});

test('hasNext governa o carregar mais e o fim da lista', async () => {
  montar(SESSAO);
  const fetchMock = jest.fn()
    .mockResolvedValueOnce(respostaJson(paginaDe([notificacao(1), notificacao(2)], true)))
    .mockResolvedValueOnce(respostaJson(paginaDe([notificacao(3)], false)));
  window.fetch = fetchMock;
  iniciar();

  await waitFor(() => expect(itens().length).toBe(2));
  expect(document.querySelector('[data-notificacoes-mais]')).not.toHaveAttribute('hidden');

  document.querySelector('[data-notificacoes-mais]').click();

  await waitFor(() => expect(itens().length).toBe(3));
  expect(fetchMock.mock.calls[1][0])
    .toBe('http://localhost:8080/api/notificacoes?page=1&size=20');
  expect(document.querySelector('[data-notificacoes-mais]')).toHaveAttribute('hidden');
  expect(document.querySelector('[data-notificacoes-fim]')).not.toHaveAttribute('hidden');
});

test('marcar uma como lida chama PATCH e tira o destaque só daquela', async () => {
  montar(SESSAO);
  const fetchMock = jest.fn().mockResolvedValue(
    respostaJson(paginaDe([notificacao(1), notificacao(2)]))
  );
  window.fetch = fetchMock;
  iniciar();

  await waitFor(() => expect(naoLidas().length).toBe(2));

  fetchMock.mockResolvedValueOnce({
    ok: true, status: 204, headers: { get: () => null }, json: async () => null
  });
  document.querySelector('[data-marcar-notificacao="1"]').click();

  await waitFor(() => expect(naoLidas().length).toBe(1));
  const [url, config] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1];
  expect(url).toBe('http://localhost:8080/api/notificacoes/1/lida');
  expect(config.method).toBe('PATCH');
  expect(document.querySelector('[data-marcar-notificacao="1"]')).toBeFalsy();
  expect(document.querySelector('[data-marcar-notificacao="2"]')).toBeTruthy();
});

test('marcar todas só repinta depois da resposta do servidor', async () => {
  montar(SESSAO);
  const fetchMock = jest.fn().mockResolvedValue(
    respostaJson(paginaDe([notificacao(1), notificacao(2)]))
  );
  window.fetch = fetchMock;
  iniciar();

  await waitFor(() => expect(naoLidas().length).toBe(2));

  let liberar;
  fetchMock.mockReturnValueOnce(new Promise((resolve) => {
    liberar = () => resolve({
      ok: true, status: 204, headers: { get: () => null }, json: async () => null
    });
  }));
  document.querySelector('[data-notificacoes-marcar-todas]').click();

  // Enquanto o servidor nao respondeu, nada muda na tela.
  await new Promise((r) => setTimeout(r, 20));
  expect(naoLidas().length).toBe(2);

  liberar();
  await waitFor(() => expect(naoLidas().length).toBe(0));
  expect(document.querySelector('[data-notificacoes-marcar-todas]')).toHaveAttribute('hidden');
});

test('falha ao marcar devolve o botão em vez de mentir que leu', async () => {
  montar(SESSAO);
  const fetchMock = jest.fn().mockResolvedValue(respostaJson(paginaDe([notificacao(1)])));
  window.fetch = fetchMock;
  iniciar();

  await waitFor(() => expect(naoLidas().length).toBe(1));

  fetchMock.mockResolvedValueOnce(respostaJson({ mensagem: 'Falhou' }, 500));
  document.querySelector('[data-marcar-notificacao="1"]').click();

  await waitFor(() =>
    expect(document.querySelector('[data-notificacoes-erro]')).not.toHaveAttribute('hidden')
  );
  expect(naoLidas().length).toBe(1);
  expect(document.querySelector('[data-marcar-notificacao="1"]').disabled).toBe(false);
});

test('link externo na notificação não vira href: só destino interno permitido', async () => {
  montar(SESSAO);
  window.fetch = jest.fn().mockResolvedValue(respostaJson(paginaDe([
    notificacao(1, { link: 'https://site-de-fora.example/phishing' }),
    notificacao(2, { link: 'javascript:alert(1)' }),
    notificacao(3, { link: '/detalhe-vaga.html?id=7' })
  ])));
  iniciar();

  await waitFor(() => expect(itens().length).toBe(3));
  expect(document.querySelector('[data-notificacao-id="1"] a')).toBeNull();
  expect(document.querySelector('[data-notificacao-id="2"] a')).toBeNull();
  expect(document.querySelector('[data-notificacao-id="3"] a').getAttribute('href'))
    .toBe('detalhe-vaga.html?id=7');
});

test('mensagem da notificação é escapada, não interpretada como HTML', async () => {
  montar(SESSAO);
  window.fetch = jest.fn().mockResolvedValue(respostaJson(paginaDe([
    notificacao(1, { mensagem: '<img src=x onerror="window.__xssNotif=1">' })
  ])));
  iniciar();

  await waitFor(() => expect(itens().length).toBe(1));
  expect(document.querySelector('.central-item__mensagem img')).toBeNull();
  expect(window.__xssNotif).toBeUndefined();
});

test('tipo fora do MVP aparece como aviso genérico, sem reintroduzir o módulo', async () => {
  montar(SESSAO);
  window.fetch = jest.fn().mockResolvedValue(respostaJson(paginaDe([
    notificacao(1, { tipo: 'EDITAL' }),
    notificacao(2, { tipo: 'CANDIDATURA' })
  ])));
  iniciar();

  await waitFor(() => expect(itens().length).toBe(2));
  expect(document.querySelector('[data-notificacao-id="1"] .central-item__tipo'))
    .toHaveTextContent('Aviso');
  expect(document.querySelector('[data-notificacao-id="2"] .central-item__tipo'))
    .toHaveTextContent('Candidatura');
});
