import { waitFor } from '@testing-library/dom';
import fs from 'fs';
import path from 'path';

const script = fs.readFileSync(
  path.join(process.cwd(), 'public', 'js', 'main.js'),
  'utf8'
);

// Usa o corpo real de detalhe-vaga.html em vez de um fragmento escrito a mao:
// assim um data-* renomeado no HTML quebra o teste, que e o ponto.
const paginaDetalhe = fs.readFileSync(
  path.join(process.cwd(), 'public', 'detalhe-vaga.html'),
  'utf8'
);
const corpoDetalhe = paginaDetalhe
  .replace(/[\s\S]*<body[^>]*>/, '')
  .replace(/<script[\s\S]*/, '');

const VAGA_ABERTA = {
  id: 7,
  titulo: 'Atriz musical',
  nomeContratante: 'Academia Teatral',
  cidade: 'Campinas',
  estado: 'SP',
  modeloTrabalho: 'PRESENCIAL',
  remuneraValor: 2500,
  descricao: 'Descrição',
  requisitos: 'Experiência',
  status: 'ABERTA',
  funcaoIds: []
};

// main.js e avaliado uma vez por teste e registra um listener de
// DOMContentLoaded no `document`, que jsdom compartilha entre os testes do
// arquivo. Sem isolar, o teste N dispara as N cadeias de inicializacao ja
// registradas e uma instancia antiga mexe no DOM da atual. Capturamos o
// listener em vez de registra-lo e disparamos so o desta instancia.
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
  document.body.innerHTML = corpoDetalhe;
  window.history.replaceState({}, '', '/detalhe-vaga.html?id=7');
  sessionStorage.clear();
  localStorage.clear();
  if (sessao) sessionStorage.setItem('palco.sessao', JSON.stringify(sessao));
}

function respostaJson(corpo, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => 'application/json' },
    // Copia a cada chamada, como o fetch real (cada .json() produz um objeto
    // novo). Devolver a mesma referencia deixava o codigo gravar
    // minhaCandidaturaId na fixture compartilhada e contaminar o teste seguinte.
    json: async () => (corpo && typeof corpo === 'object' ? { ...corpo } : corpo)
  };
}

// Roteia a resposta por URL e metodo. Depender da ordem das chamadas torna o
// teste fragil: qualquer fetch extra do main.js consumia o mockResolvedValueOnce
// destinado ao POST.
function roteador({ vaga, aoPostar, aoDeletar }) {
  return jest.fn(async (url, config = {}) => {
    const metodo = (config.method || 'GET').toUpperCase();
    if (metodo === 'POST' && url.endsWith('/candidaturas')) return aoPostar();
    if (metodo === 'DELETE' && url.includes('/candidaturas/')) return aoDeletar();
    return respostaJson(vaga);
  });
}

async function carregar(vaga) {
  window.eval(script);
  iniciarEstaInstancia();
  await waitFor(() =>
    expect(document.querySelector('[data-vaga-conteudo]')).not.toHaveAttribute('hidden')
  );
  return vaga;
}

const caixa = () => document.querySelector('[data-candidatura]');
const formulario = () => document.querySelector('[data-candidatura-form]');
const aviso = () => document.querySelector('[data-candidatura-aviso]');
const situacao = () => document.querySelector('[data-candidatura-situacao]');
const erro = () => document.querySelector('[data-candidatura-erro]');

test('visitante sem sessão é convidado a entrar e não vê o formulário', async () => {
  montar(null);
  window.fetch = jest.fn().mockResolvedValue(respostaJson(VAGA_ABERTA));
  await carregar();

  expect(aviso()).not.toHaveAttribute('hidden');
  expect(aviso().textContent).toMatch(/entre na sua conta/i);
  expect(formulario()).toHaveAttribute('hidden');
});

test('contratante não recebe formulário de candidatura', async () => {
  montar({ token: 'jwt', id: 1, tipoUsuario: 'CONTRATANTE', perfilCompleto: true });
  window.fetch = jest.fn().mockResolvedValue(respostaJson(VAGA_ABERTA));
  await carregar();

  expect(formulario()).toHaveAttribute('hidden');
  expect(aviso().textContent).toMatch(/somente artistas/i);
});

test('vaga do próprio contratante aponta para as candidaturas recebidas', async () => {
  montar({ token: 'jwt', id: 1, tipoUsuario: 'CONTRATANTE', perfilCompleto: true });
  window.fetch = jest.fn().mockResolvedValue(
    respostaJson({ ...VAGA_ABERTA, propriaDoContratante: true })
  );
  await carregar();

  expect(aviso().querySelector('a').getAttribute('href'))
    .toBe('/vagas/7/gerenciar');
});

test('artista com perfil incompleto não pode enviar e é mandado completar o perfil', async () => {
  montar({ token: 'jwt', id: 9, tipoUsuario: 'ARTISTA', perfilCompleto: false });
  window.fetch = jest.fn().mockResolvedValue(respostaJson(VAGA_ABERTA));
  await carregar();

  expect(formulario()).toHaveAttribute('hidden');
  expect(aviso().textContent).toMatch(/complete seu perfil/i);
  expect(aviso().querySelector('a').getAttribute('href')).toBe('/perfil');
});

test('vaga que não está ABERTA não aceita candidatura', async () => {
  montar({ token: 'jwt', id: 9, tipoUsuario: 'ARTISTA', perfilCompleto: true });
  window.fetch = jest.fn().mockResolvedValue(
    respostaJson({ ...VAGA_ABERTA, status: 'PAUSADA' })
  );
  await carregar();

  expect(formulario()).toHaveAttribute('hidden');
  expect(aviso().textContent).toMatch(/não está aberta/i);
});

test('artista apto envia candidatura com o artistaId da própria sessão', async () => {
  montar({ token: 'jwt', id: 9, tipoUsuario: 'ARTISTA', perfilCompleto: true });
  const fetchMock = roteador({
    vaga: VAGA_ABERTA,
    aoPostar: () => respostaJson({ id: 55, status: 'PENDENTE', dataCandidatura: '2026-09-12T10:00:00' }, 201)
  });
  window.fetch = fetchMock;
  await carregar();

  expect(formulario()).not.toHaveAttribute('hidden');

  formulario().elements.mensagemApresentacao.value = 'Tenho 5 anos de teatro musical.';
  formulario().elements.linkPortfolioCandidatura.value = 'https://exemplo.com/portfolio';
  formulario().dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  await waitFor(() => expect(situacao()).not.toHaveAttribute('hidden'));

  const [url, config] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1];
  expect(url).toBe('http://localhost:8080/api/candidaturas');
  expect(config.method).toBe('POST');
  expect(JSON.parse(config.body)).toEqual({
    vagaId: 7,
    artistaId: 9,
    mensagemApresentacao: 'Tenho 5 anos de teatro musical.',
    linkPortfolioCandidatura: 'https://exemplo.com/portfolio'
  });
  expect(document.querySelector('[data-candidatura-status-texto]'))
    .toHaveTextContent('Candidatura enviada');
  expect(formulario()).toHaveAttribute('hidden');
});

test('campos obrigatórios do contrato bloqueiam o envio antes de chamar a API', async () => {
  montar({ token: 'jwt', id: 9, tipoUsuario: 'ARTISTA', perfilCompleto: true });
  const fetchMock = roteador({ vaga: VAGA_ABERTA });
  window.fetch = fetchMock;
  await carregar();

  const chamadasAntes = fetchMock.mock.calls.length;
  formulario().dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  await waitFor(() => expect(erro()).not.toHaveAttribute('hidden'));
  expect(fetchMock.mock.calls.length).toBe(chamadasAntes);
  expect(erro().textContent).toMatch(/mensagem de apresentação/i);
});

test('duplicata (409) informa que a candidatura já existe, sem fingir novo envio', async () => {
  montar({ token: 'jwt', id: 9, tipoUsuario: 'ARTISTA', perfilCompleto: true });
  const fetchMock = roteador({
    vaga: VAGA_ABERTA,
    aoPostar: () => respostaJson({ mensagem: 'Já existe candidatura para esta vaga e artista.' }, 409)
  });
  window.fetch = fetchMock;
  await carregar();

  formulario().elements.mensagemApresentacao.value = 'Mensagem';
  formulario().elements.linkPortfolioCandidatura.value = 'https://exemplo.com';
  formulario().dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  await waitFor(() => expect(erro()).not.toHaveAttribute('hidden'));
  expect(erro().textContent).toMatch(/já se candidatou/i);
  expect(situacao()).toHaveAttribute('hidden');
  expect(aviso()).toHaveTextContent('Recarregue a vaga');
});

test('perfil incompleto vindo do servidor (422) preserva o que foi digitado', async () => {
  montar({ token: 'jwt', id: 9, tipoUsuario: 'ARTISTA', perfilCompleto: true });
  const fetchMock = roteador({
    vaga: VAGA_ABERTA,
    aoPostar: () => respostaJson({ mensagem: 'Complete seu perfil antes de se candidatar.' }, 422)
  });
  window.fetch = fetchMock;
  await carregar();

  formulario().elements.mensagemApresentacao.value = 'Texto que não pode sumir';
  formulario().elements.linkPortfolioCandidatura.value = 'https://exemplo.com';
  formulario().dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  await waitFor(() => expect(erro()).not.toHaveAttribute('hidden'));
  expect(erro().textContent).toMatch(/complete seu perfil/i);
  expect(formulario().elements.mensagemApresentacao.value).toBe('Texto que não pode sumir');
  expect(formulario()).not.toHaveAttribute('hidden');
});

test('candidatura PENDENTE oferece retirada e o DELETE marca RETIRADA', async () => {
  montar({ token: 'jwt', id: 9, tipoUsuario: 'ARTISTA', perfilCompleto: true });
  const fetchMock = roteador({
    vaga: { ...VAGA_ABERTA, minhaCandidaturaId: 55, statusMinhaCandidatura: 'PENDENTE' },
    aoDeletar: () => ({ ok: true, status: 204, headers: { get: () => null }, json: async () => null })
  });
  window.fetch = fetchMock;
  await carregar();

  const botao = document.querySelector('[data-candidatura-retirar]');
  expect(botao).not.toHaveAttribute('hidden');

  window.confirm = jest.fn(() => true);
  botao.click();

  await waitFor(() =>
    expect(document.querySelector('[data-candidatura-status-texto]')).toHaveTextContent('Retirada')
  );
  const [url, config] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1];
  expect(url).toBe('http://localhost:8080/api/candidaturas/55');
  expect(config.method).toBe('DELETE');
  expect(botao).toHaveAttribute('hidden');
});

test('candidatura ACEITA não oferece retirada, como o servidor não permite', async () => {
  montar({ token: 'jwt', id: 9, tipoUsuario: 'ARTISTA', perfilCompleto: true });
  window.fetch = jest.fn().mockResolvedValue(
    respostaJson({ ...VAGA_ABERTA, minhaCandidaturaId: 55, statusMinhaCandidatura: 'ACEITA' })
  );
  await carregar();

  expect(document.querySelector('[data-candidatura-retirar]')).toHaveAttribute('hidden');
  expect(document.querySelector('[data-candidatura-status-texto]')).toHaveTextContent('Aprovada');
});

test('retirada cancelada no confirm não chama a API', async () => {
  montar({ token: 'jwt', id: 9, tipoUsuario: 'ARTISTA', perfilCompleto: true });
  const fetchMock = roteador({
    vaga: { ...VAGA_ABERTA, minhaCandidaturaId: 55, statusMinhaCandidatura: 'PENDENTE' }
  });
  window.fetch = fetchMock;
  await carregar();

  window.confirm = jest.fn(() => false);
  const antes = fetchMock.mock.calls.length;
  document.querySelector('[data-candidatura-retirar]').click();

  expect(fetchMock.mock.calls.length).toBe(antes);
  expect(document.querySelector('[data-candidatura-status-texto]'))
    .toHaveTextContent('Candidatura enviada');
});
