import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import apiClient from '../../services/api/apiClient';
import VacancySearchPage from './VacancySearchPage';

jest.mock('../../services/api/apiClient', () => ({
  __esModule: true,
  default: { get: jest.fn() },
}));

const vacancy = {
  id: 5,
  titulo: 'Ilustradora para exposição',
  nomeContratante: 'Casa Aurora',
  cidade: 'Curitiba',
  estado: 'PR',
  modeloTrabalho: 'HIBRIDO',
  tipoContrato: 'PROJETO',
  remuneraValor: 2500,
  descricao: 'Criação de peças autorais.',
};

function page(overrides = {}) {
  return {
    content: [],
    nextCursor: null,
    hasMore: false,
    vagasCanceladasComCandidatura: [],
    nextCursorCanceladas: null,
    hasMoreCanceladas: false,
    ...overrides,
  };
}

function renderSearch(path = '/vagas') {
  return render(
    <MemoryRouter
      initialEntries={[path]}
      future={{ v7_relativeSplatPath: true, v7_startTransition: true }}
    >
      <VacancySearchPage />
    </MemoryRouter>
  );
}

let observerCallbacks;

beforeEach(() => {
  apiClient.get.mockReset();
  window.localStorage.clear();
  window.sessionStorage.clear();
  observerCallbacks = [];
  window.IntersectionObserver = class {
    constructor(callback) { observerCallbacks.push(callback); }
    observe() {}
    disconnect() {}
  };
  window.matchMedia = jest.fn(() => ({ matches: true }));
  window.scrollTo = jest.fn();
});

test('é pública, usa size 20 e renderiza card com rota direta React', async () => {
  apiClient.get.mockResolvedValue(page({ content: [vacancy] }));
  renderSearch();

  expect(await screen.findByRole('heading', { name: vacancy.titulo })).toBeInTheDocument();
  expect(apiClient.get).toHaveBeenCalledWith('/vagas?size=20');
  expect(screen.getByRole('link', { name: 'Ver detalhes' })).toHaveAttribute('href', '/vagas/5');
  expect(screen.getByText(/2\.500,00/)).toBeInTheDocument();
});

test('lê filtros da URL e os envia ao contrato atual do backend', async () => {
  apiClient.get.mockResolvedValue(page());
  renderSearch('/vagas?titulo=cantor&cidade=S%C3%A3o+Paulo&modeloTrabalho=REMOTO');

  await screen.findByText('Nenhuma vaga encontrada com esses filtros.');
  expect(screen.getByLabelText('Título')).toHaveValue('cantor');
  expect(screen.getByLabelText('Cidade')).toHaveValue('São Paulo');
  expect(screen.getByLabelText('Modelo de trabalho')).toHaveValue('REMOTO');
  expect(apiClient.get).toHaveBeenCalledWith(
    '/vagas?titulo=cantor&cidade=S%C3%A3o+Paulo&modeloTrabalho=REMOTO&size=20'
  );
});

test('aplica e limpa filtros mantendo a URL como fonte de estado', async () => {
  apiClient.get.mockResolvedValue(page());
  renderSearch();
  await screen.findByText('Nenhuma vaga encontrada com esses filtros.');

  fireEvent.change(screen.getByLabelText('Empresa ou contratante'), { target: { value: 'Palco Sul' } });
  fireEvent.change(screen.getByLabelText('Estado'), { target: { value: 'rs' } });
  fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));

  await waitFor(() => expect(apiClient.get).toHaveBeenCalledWith('/vagas?empresa=Palco+Sul&estado=RS&size=20'));
  fireEvent.click(screen.getByRole('button', { name: 'Limpar' }));
  await waitFor(() => expect(apiClient.get).toHaveBeenLastCalledWith('/vagas?size=20'));
});

test('valida faixa salarial antes de chamar a API novamente', async () => {
  apiClient.get.mockResolvedValue(page());
  renderSearch();
  await screen.findByText('Nenhuma vaga encontrada com esses filtros.');

  fireEvent.change(screen.getByLabelText('Remuneração mínima'), { target: { value: '5000' } });
  fireEvent.change(screen.getByLabelText('Remuneração máxima'), { target: { value: '1000' } });
  fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));

  expect(screen.getByRole('alert')).toHaveTextContent('mínima não pode ser maior');
  expect(apiClient.get).toHaveBeenCalledTimes(1);
});

test('exibe loading inicial, erro recuperável e estado vazio', async () => {
  let rejectRequest;
  apiClient.get.mockReturnValueOnce(new Promise((resolve, reject) => { rejectRequest = reject; }));
  renderSearch();

  expect(screen.getByText('Carregando oportunidades…')).toBeInTheDocument();
  await act(async () => rejectRequest(new Error('Falha controlada')));
  expect(await screen.findByRole('alert')).toHaveTextContent('Falha controlada');

  apiClient.get.mockResolvedValueOnce(page());
  fireEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }));
  expect(await screen.findByText('Nenhuma vaga encontrada com esses filtros.')).toBeInTheDocument();
});

test('pagina incrementalmente com cursor independente e elimina duplicatas', async () => {
  let feedRequest = 0;
  apiClient.get.mockImplementation((path) => {
    if (path.includes('/similares')) return Promise.resolve(page());
    feedRequest += 1;
    return Promise.resolve(feedRequest === 1
      ? page({ content: [vacancy], nextCursor: 5, hasMore: true })
      : page({ content: [vacancy, { ...vacancy, id: 6, titulo: 'Segunda vaga' }] }));
  });
  renderSearch();

  await screen.findByRole('heading', { name: vacancy.titulo });
  expect(observerCallbacks.length).toBeGreaterThan(0);
  act(() => observerCallbacks[observerCallbacks.length - 1]([{ isIntersecting: true }]));

  expect(await screen.findByRole('heading', { name: 'Segunda vaga' })).toBeInTheDocument();
  expect(apiClient.get).toHaveBeenCalledWith('/vagas?cursor=5&size=20');
  expect(screen.getAllByRole('heading', { name: vacancy.titulo })).toHaveLength(1);
  expect(screen.getByText('Você chegou ao fim das vagas disponíveis.')).toBeInTheDocument();
});

test('renderiza Vagas Similares pelo contexto da primeira vaga aberta', async () => {
  const similar = { ...vacancy, id: 6, titulo: 'Direção de arte', status: 'ABERTA' };
  apiClient.get.mockImplementation((path) => Promise.resolve(
    path.includes('/similares') ? page({ content: [similar] }) : page({ content: [{ ...vacancy, status: 'ABERTA' }] })
  ));

  renderSearch();

  expect(await screen.findByRole('heading', { name: 'Vagas Similares' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: similar.titulo })).toBeInTheDocument();
  expect(apiClient.get).toHaveBeenCalledWith('/vagas/5/similares?size=3');
});

test('não renderiza carrossel vazio nem aceita origem ou status não aberto', async () => {
  apiClient.get.mockImplementation((path) => Promise.resolve(path.includes('/similares')
    ? page({ content: [
      { ...vacancy, status: 'ABERTA' },
      { ...vacancy, id: 7, titulo: 'Vaga pausada', status: 'PAUSADA' },
    ] })
    : page({ content: [{ ...vacancy, status: 'ABERTA' }] })));

  renderSearch();

  await waitFor(() => expect(apiClient.get).toHaveBeenCalledWith('/vagas/5/similares?size=3'));
  expect(screen.queryByRole('heading', { name: 'Vagas Similares' })).not.toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Vaga pausada' })).not.toBeInTheDocument();
});

test('falha de similares não inutiliza o feed principal', async () => {
  apiClient.get.mockImplementation((path) => path.includes('/similares')
    ? Promise.reject(new Error('Falha isolada'))
    : Promise.resolve(page({ content: [{ ...vacancy, status: 'ABERTA' }] })));

  renderSearch();

  expect(await screen.findByRole('heading', { name: vacancy.titulo })).toBeInTheDocument();
  await waitFor(() => expect(apiClient.get).toHaveBeenCalledWith('/vagas/5/similares?size=3'));
  expect(screen.queryByRole('heading', { name: 'Vagas Similares' })).not.toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('mostra cancelada somente no bloco contextual retornado e sem ação de candidatura', async () => {
  apiClient.get.mockResolvedValue(page({
    vagasCanceladasComCandidatura: [{ ...vacancy, id: 9, titulo: 'Vaga encerrada' }],
  }));
  renderSearch();

  expect(await screen.findByRole('heading', { name: 'Vaga encerrada' })).toBeInTheDocument();
  expect(screen.getByText('Vaga Cancelada')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: /Vagas canceladas em que você se candidatou/i })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /candidatar/i })).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: /candidatar/i })).not.toBeInTheDocument();
});

test('conteúdo não confiável permanece texto e campos opcionais têm fallback', async () => {
  const malicious = '<img src=x onerror=window.__rf03_pwned=true>';
  apiClient.get.mockResolvedValue(page({ content: [{ id: 12, titulo: malicious }] }));
  renderSearch();

  expect(await screen.findByText(malicious)).toBeInTheDocument();
  expect(document.querySelector('.vaga-busca-card img[src="x"]')).toBeNull();
  expect(screen.getByText('Contratante não informado')).toBeInTheDocument();
  expect(screen.getByText('Descrição não informada.')).toBeInTheDocument();
});
