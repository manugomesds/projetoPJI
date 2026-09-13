import fs from 'fs';
import path from 'path';

const publicDir = path.join(process.cwd(), 'public');
const buscaHtml = fs.readFileSync(path.join(publicDir, 'buscar-vagas.html'), 'utf8');
const homeHtml = fs.readFileSync(path.join(publicDir, 'home.html'), 'utf8');
const apiScript = fs.readFileSync(path.join(publicDir, 'js', 'vagas-api.js'), 'utf8');
const buscaScript = fs.readFileSync(path.join(publicDir, 'js', 'buscar-vagas.js'), 'utf8');
const homeScript = fs.readFileSync(path.join(publicDir, 'js', 'home.js'), 'utf8');
const mainScript = fs.readFileSync(path.join(publicDir, 'js', 'main.js'), 'utf8');

function corpo(html) {
  return html.match(/<body[^>]*>([\s\S]*?)<\/body>/i)[1]
    .replace(/<script[\s\S]*?<\/script>/gi, '');
}

function resposta(corpoJson, status = 200) {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => 'application/json' },
    json: () => Promise.resolve(corpoJson)
  });
}

function pagina(conteudo = [], extras = {}) {
  return {
    content: conteudo,
    nextCursor: null,
    hasMore: false,
    vagasCanceladasComCandidatura: [],
    nextCursorCanceladas: null,
    hasMoreCanceladas: false,
    ...extras
  };
}

function vaga(id, extras = {}) {
  return {
    id,
    titulo: `Vaga ${id}`,
    nomeContratante: 'Casa Palco',
    cidade: 'São Paulo',
    estado: 'SP',
    modeloTrabalho: 'PRESENCIAL',
    tipoContrato: 'Evento',
    categoria: 'Música',
    remuneraValor: 500,
    dataLimiteCandidatura: '2026-12-10',
    descricao: 'Oportunidade artística real.',
    fotos: [],
    propriaDoContratante: false,
    cancelada: false,
    status: 'ABERTA',
    ...extras
  };
}

async function estabilizar() {
  for (let i = 0; i < 10; i += 1) await Promise.resolve();
}

test('helper central gera a rota React do detalhe público com ID codificado', () => {
  window.eval(apiScript);

  expect(window.PalcoVagas.urlDetalhe(7)).toBe('/vagas/7');
  expect(window.PalcoVagas.urlDetalhe('7/8')).toBe('/vagas/7%2F8');
});

let observar;

function montarBusca(fetchMock, url = '/buscar-vagas.html') {
  window.history.replaceState(null, '', url);
  document.body.innerHTML = corpo(buscaHtml);
  document.body.className = 'pagina-buscar-vagas';
  delete document.body.dataset.buscaVagasIniciada;
  sessionStorage.clear();
  localStorage.clear();
  window.fetch = fetchMock;
  window.scrollTo = jest.fn();
  window.matchMedia = jest.fn(() => ({ matches: true, addEventListener: jest.fn() }));
  window.requestAnimationFrame = jest.fn(callback => {
    callback(1000);
    return 1;
  });
  observar = null;
  window.IntersectionObserver = class {
    constructor(callback) {
      observar = callback;
    }
    observe() {}
    unobserve() {}
    disconnect() {}
  };
  window.eval(apiScript);
  window.eval(buscaScript);
  window.PalcoBuscaVagas.iniciar();
}

test('monta filtros com URLSearchParams, omite vazios e solicita primeira página size=20', async () => {
  const fetchMock = jest.fn(() => resposta(pagina()));
  montarBusca(fetchMock, '/buscar-vagas.html?titulo=cantor&cidade=S%C3%A3o%20Paulo&modeloTrabalho=PRESENCIAL');
  await estabilizar();

  expect(fetchMock).toHaveBeenCalledTimes(1);
  const url = new URL(fetchMock.mock.calls[0][0]);
  expect(url.pathname).toBe('/api/vagas');
  expect(url.searchParams.get('titulo')).toBe('cantor');
  expect(url.searchParams.get('cidade')).toBe('São Paulo');
  expect(url.searchParams.get('modeloTrabalho')).toBe('PRESENCIAL');
  expect(url.searchParams.get('size')).toBe('20');
  expect(url.searchParams.has('empresa')).toBe(false);
  expect(url.searchParams.has('cursor')).toBe(false);
});

test('usa nextCursor na segunda página, acumula cards e hasMore=false bloqueia nova request', async () => {
  const fetchMock = jest.fn()
    .mockImplementationOnce(() => resposta(pagina([vaga(5)], { nextCursor: 5, hasMore: true })))
    .mockImplementationOnce(() => resposta(pagina([vaga(9)])));
  montarBusca(fetchMock);
  await estabilizar();

  observar([{ isIntersecting: true }]);
  await estabilizar();
  expect(new URL(fetchMock.mock.calls[1][0]).searchParams.get('cursor')).toBe('5');
  expect(document.querySelectorAll('[data-lista-vagas] .vaga-busca-card')).toHaveLength(2);

  observar([{ isIntersecting: true }]);
  await estabilizar();
  expect(fetchMock).toHaveBeenCalledTimes(2);
  expect(document.querySelector('[data-fim-lista]')).not.toHaveAttribute('hidden');
});

test('aplicar novo filtro limpa cards, reseta cursores e reflete a busca na URL', async () => {
  const fetchMock = jest.fn()
    .mockImplementationOnce(() => resposta(pagina([vaga(5)], { nextCursor: 5, hasMore: true })))
    .mockImplementationOnce(() => resposta(pagina([vaga(12, { titulo: 'Atriz para teatro' })])));
  montarBusca(fetchMock);
  await estabilizar();

  document.querySelector('[name="titulo"]').value = 'teatro';
  document.querySelector('[data-form-filtros]').dispatchEvent(
    new Event('submit', { bubbles: true, cancelable: true })
  );
  await estabilizar();

  const parametros = new URL(fetchMock.mock.calls[1][0]).searchParams;
  expect(parametros.get('titulo')).toBe('teatro');
  expect(parametros.has('cursor')).toBe(false);
  expect(window.location.search).toBe('?titulo=teatro');
  expect(document.querySelectorAll('[data-lista-vagas] .vaga-busca-card')).toHaveLength(1);
  expect(document.querySelector('[data-lista-vagas]')).toHaveTextContent('Atriz para teatro');
  expect(document.querySelector('[data-lista-vagas]')).not.toHaveTextContent('Vaga 5');
});

test('mostra estado vazio dentro da página sem alert', async () => {
  const fetchMock = jest.fn(() => resposta(pagina()));
  window.alert = jest.fn();
  montarBusca(fetchMock);
  await estabilizar();

  expect(document.querySelector('[data-estado-vazio]')).not.toHaveAttribute('hidden');
  expect(document.querySelector('[data-estado-vazio]')).toHaveTextContent('Nenhuma vaga encontrada');
  expect(window.alert).not.toHaveBeenCalled();
});

test('mostra erro amigável e Tentar novamente recupera a listagem', async () => {
  const fetchMock = jest.fn()
    .mockRejectedValueOnce(new Error('API indisponível'))
    .mockImplementationOnce(() => resposta(pagina([vaga(2)])));
  montarBusca(fetchMock);
  await estabilizar();

  expect(document.querySelector('[data-estado-erro]')).not.toHaveAttribute('hidden');
  expect(document.querySelector('[data-erro-texto]')).toHaveTextContent('API indisponível');
  document.querySelector('[data-tentar-novamente]').click();
  await estabilizar();
  expect(document.querySelector('[data-estado-erro]')).toHaveAttribute('hidden');
  expect(document.querySelector('[data-lista-vagas]')).toHaveTextContent('Vaga 2');
});

test('card usa ID real, badge Sua vaga e textContent impede markup/XSS', async () => {
  const maliciosa = vaga(77, {
    titulo: '<img src=x onerror="window.rf03Xss=true">',
    descricao: '<script>window.rf03Xss=true</script>',
    propriaDoContratante: true
  });
  const fetchMock = jest.fn(() => resposta(pagina([maliciosa])));
  montarBusca(fetchMock);
  await estabilizar();

  const card = document.querySelector('.vaga-busca-card');
  expect(card.querySelector('.vaga-busca-card__titulo')).toHaveTextContent('<img src=x');
  expect(card.querySelector('.vaga-busca-card__titulo img')).toBeNull();
  expect(card).toHaveTextContent('Sua vaga');
  expect(card.querySelector('.vaga-busca-card__link').getAttribute('href'))
    .toBe('/vagas/77');
  expect(window.rf03Xss).toBeUndefined();
});

test('canceladas ficam em seção separada, recebem badge e não oferecem candidatura nova', async () => {
  const cancelada = vaga(30, { status: 'CANCELADA', cancelada: true });
  const fetchMock = jest.fn(() => resposta(pagina([], {
    vagasCanceladasComCandidatura: [cancelada]
  })));
  montarBusca(fetchMock);
  await estabilizar();

  const secao = document.querySelector('[data-secao-canceladas]');
  expect(secao).not.toHaveAttribute('hidden');
  expect(document.querySelector('[data-lista-vagas]')).toBeEmptyDOMElement();
  expect(secao).toHaveTextContent('Vaga Cancelada');
  expect(secao).not.toHaveTextContent(/candidatar/i);
  expect(secao.querySelector('a').getAttribute('href')).toBe('/vagas/30');
});

function prepararLayoutLanding() {
  const medidas = [
    { client: 769, scroll: 2552, item: 300, passo: 328, inicio: 234 },
    { client: 892, scroll: 1866, item: 240, passo: 290, inicio: 326 },
    { client: 1000, scroll: 832, item: 190, passo: 214, inicio: 0 }
  ];
  document.querySelectorAll('[data-carrossel-trilha]').forEach((trilha, indice) => {
    const medida = medidas[indice];
    Object.defineProperty(trilha, 'clientWidth', { configurable: true, value: medida.client });
    Object.defineProperty(trilha, 'scrollWidth', { configurable: true, value: medida.scroll });
    trilha.scrollLeft = 0;
    Array.from(trilha.children).forEach((item, posicao) => {
      Object.defineProperty(item, 'offsetWidth', { configurable: true, value: medida.item });
      Object.defineProperty(item, 'offsetLeft', {
        configurable: true,
        value: medida.inicio + posicao * medida.passo
      });
    });
  });
}

function montarLanding(fetchMock) {
  window.history.replaceState(null, '', '/home.html');
  document.body.innerHTML = corpo(homeHtml);
  document.body.className = 'pagina-home';
  window.fetch = fetchMock;
  window.matchMedia = jest.fn(() => ({ matches: true, addEventListener: jest.fn() }));
  let tempo = 0;
  window.requestAnimationFrame = jest.fn(callback => {
    tempo += 400;
    callback(tempo);
    return tempo;
  });
  window.cancelAnimationFrame = jest.fn();
  window.ResizeObserver = class { observe() {} disconnect() {} };
  prepararLayoutLanding();
  window.eval(apiScript);
  window.eval(homeScript);
  window.PalcoHome.iniciarCarrosseis();
}

test('landing mostra erro sem vagas demonstrativas se a API falhar', async () => {
  montarLanding(jest.fn(() => Promise.reject(new Error('offline'))));
  await window.PalcoHome.carregarVagasReaisLanding();

  const trilha = document.querySelector('[data-vagas-landing]');
  expect(trilha.children).toHaveLength(0);
  expect(trilha.dataset.fonteVagas).toBe('erro');
  expect(trilha).toHaveTextContent('Não foi possível carregar as vagas');
});

test('landing troca por vagas reais e o carrossel navega após refresh sem duplicar listeners', async () => {
  const reais = [vaga(101), vaga(102), vaga(103)];
  const fetchMock = jest.fn(() => resposta(pagina(reais)));
  montarLanding(fetchMock);
  await window.PalcoHome.carregarVagasReaisLanding();

  const trilha = document.querySelector('[data-vagas-landing]');
  expect(trilha.children).toHaveLength(3);
  expect(trilha.dataset.fonteVagas).toBe('api');
  expect(trilha.querySelectorAll('a')[0].getAttribute('href')).toBe('/vagas/101');

  Object.defineProperty(trilha, 'clientWidth', { configurable: true, value: 769 });
  Object.defineProperty(trilha, 'scrollWidth', { configurable: true, value: 1300 });
  Array.from(trilha.children).forEach((item, indice) => {
    Object.defineProperty(item, 'offsetWidth', { configurable: true, value: 300 });
    Object.defineProperty(item, 'offsetLeft', { configurable: true, value: 234 + indice * 328 });
  });
  const carrossel = trilha.closest('[data-carrossel]');
  carrossel.dispatchEvent(new Event('palco:carrossel-atualizar'));
  trilha.scrollLeft = 423;
  carrossel.querySelector('[data-carrossel-proximo]').click();
  expect(carrossel).toHaveAttribute('data-carrossel-indice', '2');
  expect(trilha.children[2]).toHaveClass('vaga-mini--destaque');
});

test('detalhe existente consulta o endpoint backend de similares por tags', () => {
  expect(mainScript).toMatch(/\/vagas\/['"]?\s*\+\s*encodeURIComponent\(vaga\.id\)\s*\+\s*['"]\/similares\?size=3/);
  expect(mainScript).not.toMatch(/var relacionadas = await api\('\/vagas\?size=4'\)/);
});

test('fluxos públicos adicionais usam React e os destinos legados deliberados permanecem', () => {
  expect(mainScript).toContain("link('Ver vaga', '/vagas/' + encodeURIComponent(vaga.id)");
  expect(mainScript).toContain(`href="/vagas/' + encodeURIComponent(item.id) + '">`);
  expect(mainScript).toContain("'/detalhe-vaga.html'");
  expect(mainScript).toContain("paginaAtual !== 'detalhe-vaga.html'");
  expect(mainScript).toContain("/vagas/' + encodeURIComponent(vaga.id) + '/gerenciar");
  expect(mainScript).toContain("window.location.href = '/vagas/' + encodeURIComponent(id) + '/gerenciar'");
});
