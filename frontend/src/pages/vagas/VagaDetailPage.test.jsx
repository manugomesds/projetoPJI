import { act, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { SESSION_STORAGE_KEY } from '../../auth/sessionService';
import ApiError from '../../services/api/ApiError';
import apiClient from '../../services/api/apiClient';
import VagaDetailPage from './VagaDetailPage';

jest.mock('../../services/api/apiClient', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const vaga = {
  id: 42,
  titulo: 'Guitarrista para festival',
  nomeContratante: 'Produções Aurora',
  cidade: 'São Paulo',
  estado: 'SP',
  modeloTrabalho: 'PRESENCIAL',
  remuneraValor: 1234.56,
  descricao: 'Apresentação no palco principal.',
  requisitos: 'Experiência com repertório autoral.',
  status: 'ABERTA',
  tagIds: [2, 7],
};

function renderPage(path = '/vagas/42', routePath = '/vagas/:id') {
  return render(
    <MemoryRouter
      initialEntries={[path]}
      future={{ v7_relativeSplatPath: true, v7_startTransition: true }}
    >
      <Routes>
        <Route path={routePath} element={<VagaDetailPage />} />
      </Routes>
    </MemoryRouter>
  );
}

function authenticateAs(tipoUsuario) {
  window.sessionStorage.setItem(
    SESSION_STORAGE_KEY,
    JSON.stringify({ token: `jwt-${tipoUsuario.toLowerCase()}`, tipoUsuario })
  );
}

beforeEach(() => {
  apiClient.get.mockReset();
  apiClient.post.mockReset();
  window.localStorage.clear();
  window.sessionStorage.clear();
  document.title = 'Palco';
});

test('ID válido realiza o GET correto', async () => {
  apiClient.get.mockResolvedValue(vaga);
  renderPage('/vagas/42');

  await screen.findByRole('heading', { name: vaga.titulo });
  expect(apiClient.get).toHaveBeenCalledTimes(1);
  expect(apiClient.get).toHaveBeenCalledWith('/vagas/42');
});

test('exibe loading enquanto a resposta é aguardada', async () => {
  let resolveRequest;
  apiClient.get.mockReturnValue(
    new Promise((resolve) => {
      resolveRequest = resolve;
    })
  );
  renderPage();

  expect(screen.getByRole('status')).toHaveTextContent('Carregando vaga…');

  await act(async () => resolveRequest(vaga));
  expect(await screen.findByRole('heading', { name: vaga.titulo })).toBeInTheDocument();
});

test('renderiza o título retornado', async () => {
  apiClient.get.mockResolvedValue(vaga);
  renderPage();

  expect(await screen.findByRole('heading', { name: vaga.titulo })).toBeInTheDocument();
});

test('renderiza o nome do contratante', async () => {
  apiClient.get.mockResolvedValue(vaga);
  renderPage();

  expect(await screen.findByText('Produções Aurora')).toBeInTheDocument();
});

test('renderiza cidade e estado', async () => {
  apiClient.get.mockResolvedValue(vaga);
  renderPage();

  expect(await screen.findByText(/São Paulo\/SP/)).toBeInTheDocument();
});

test('formata a remuneração em reais', async () => {
  apiClient.get.mockResolvedValue(vaga);
  renderPage();

  expect(await screen.findByText(/R\$\s*1\.234,56/)).toBeInTheDocument();
});

test('renderiza a descrição', async () => {
  apiClient.get.mockResolvedValue(vaga);
  renderPage();

  expect(await screen.findByText(vaga.descricao)).toBeInTheDocument();
});

test('renderiza os requisitos', async () => {
  apiClient.get.mockResolvedValue(vaga);
  renderPage();

  expect(await screen.findByText(vaga.requisitos)).toBeInTheDocument();
});

test('não quebra quando campos opcionais estão ausentes', async () => {
  apiClient.get.mockResolvedValue({
    titulo: 'Vaga sem opcionais',
    descricao: 'Descrição disponível.',
    requisitos: 'Requisitos disponíveis.',
    status: 'ABERTA',
  });
  renderPage();

  expect(await screen.findByRole('heading', { name: 'Vaga sem opcionais' })).toBeInTheDocument();
  expect(screen.queryByLabelText('Áreas da vaga')).not.toBeInTheDocument();
});

test('renderiza as tags quando presentes', async () => {
  apiClient.get.mockResolvedValue(vaga);
  renderPage();

  expect(await screen.findByText('Área #2')).toBeInTheDocument();
  expect(screen.getByText('Área #7')).toBeInTheDocument();
});

test('404 produz o estado NotFound da vaga', async () => {
  apiClient.get.mockRejectedValue(
    new ApiError({ status: 404, message: 'Vaga não encontrada.', body: null })
  );
  renderPage();

  expect(
    await screen.findByRole('heading', { name: 'Não foi possível abrir esta vaga' })
  ).toBeInTheDocument();
  expect(screen.getByText('Vaga não encontrada.')).toBeInTheDocument();
  expect(
    within(screen.getByRole('alert')).getByRole('link', { name: 'Voltar ao painel' })
  ).toHaveAttribute('href', '/dashboard');
});

test('500 produz o ErrorState preservando a mensagem', async () => {
  apiClient.get.mockRejectedValue(
    new ApiError({ status: 500, message: 'Falha interna temporária.', body: null })
  );
  renderPage();

  expect(
    await screen.findByRole('heading', { name: 'Não foi possível abrir esta vaga' })
  ).toBeInTheDocument();
  expect(screen.getByRole('alert')).toHaveTextContent('Falha interna temporária.');
});

test('ausência de ID não chama a API', () => {
  renderPage('/vagas', '/vagas');

  expect(
    screen.getByRole('heading', { name: 'Não foi possível abrir esta vaga' })
  ).toBeInTheDocument();
  expect(screen.getByRole('alert')).toHaveTextContent('Identificador de vaga inválido.');
  expect(apiClient.get).not.toHaveBeenCalled();
});

test('ID inválido não chama a API', () => {
  renderPage('/vagas/inválido');

  expect(screen.getByRole('alert')).toHaveTextContent('Identificador de vaga inválido.');
  expect(apiClient.get).not.toHaveBeenCalled();
});

test('renderiza caracteres especiais como texto sem criar HTML', async () => {
  const untrustedText = '<script>window.comprometido = true</script> & Música';
  apiClient.get.mockResolvedValue({ ...vaga, descricao: untrustedText });
  const { container } = renderPage();

  expect(await screen.findByText(untrustedText)).toBeInTheDocument();
  expect(container.querySelector('script')).not.toBeInTheDocument();
  expect(window.comprometido).toBeUndefined();
});

test('visitante anônimo consegue carregar a tela', async () => {
  expect(window.localStorage).toHaveLength(0);
  expect(window.sessionStorage).toHaveLength(0);
  apiClient.get.mockResolvedValue(vaga);
  renderPage();

  expect(await screen.findByRole('heading', { name: vaga.titulo })).toBeInTheDocument();
  expect(apiClient.get).toHaveBeenCalledWith('/vagas/42');
  expect(screen.queryByRole('heading', { name: 'Artistas sugeridos' })).not.toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Vagas similares' })).not.toBeInTheDocument();
});

test('proprietário vê ações administrativas e candidatos ordenados pelo backend', async () => {
  authenticateAs('CONTRATANTE');
  const ownerVacancy = { ...vaga, propriaDoContratante: true };
  const candidates = {
    content: [
      {
        candidaturaId: 91,
        artistaId: 7,
        nomeArtista: 'Artista Aurora',
        localizacao: 'São Paulo/SP',
        biografia: 'Cantora e compositora.',
        avatarUrl: '/api/usuarios/7/avatar',
        tagsCoincidentes: [2, 7],
        quantidadeTagsCoincidentes: 2,
        email: 'privado@example.com',
        telefone: '11999999999',
        mensagemApresentacao: 'Mensagem reservada da candidatura.',
      },
    ],
  };
  apiClient.get.mockImplementation((path) => {
    if (path === '/vagas/42') return Promise.resolve(ownerVacancy);
    if (path === '/vagas/42/candidaturas?page=0&size=3') return Promise.resolve(candidates);
    return Promise.reject(new Error(`GET inesperado: ${path}`));
  });

  renderPage();

  expect(await screen.findByRole('heading', { name: 'Artistas sugeridos' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Gerenciar vaga' })).toHaveAttribute('href', '/vagas/42/gerenciar');
  expect(screen.getByRole('link', { name: 'Editar vaga' })).toHaveAttribute('href', '/vagas/42/editar');
  expect(screen.getByRole('link', { name: 'Cancelar vaga' })).toHaveAttribute('href', '/vagas/42/gerenciar#cancelar');
  expect(await screen.findByRole('heading', { name: 'Artista Aurora' })).toBeInTheDocument();
  expect(screen.getByText('2 áreas compatíveis')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Ver perfil público' })).toHaveAttribute('href', '/perfis/ARTISTA/7');
  expect(screen.queryByText('privado@example.com')).not.toBeInTheDocument();
  expect(screen.queryByText('11999999999')).not.toBeInTheDocument();
  expect(screen.queryByText('Mensagem reservada da candidatura.')).not.toBeInTheDocument();
  expect(apiClient.get).toHaveBeenCalledWith('/vagas/42/candidaturas?page=0&size=3');
  expect(apiClient.get).not.toHaveBeenCalledWith(expect.stringContaining('/similares'));
});

test('proprietário de vaga final mantém gestão sem editar ou cancelar', async () => {
  authenticateAs('CONTRATANTE');
  apiClient.get.mockImplementation((path) => Promise.resolve(
    path === '/vagas/42'
      ? { ...vaga, status: 'ENCERRADA', propriaDoContratante: true }
      : { content: [] }
  ));

  renderPage();

  expect(await screen.findByRole('link', { name: 'Gerenciar vaga' })).toBeInTheDocument();
  expect(screen.queryByRole('link', { name: 'Editar vaga' })).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: 'Cancelar vaga' })).not.toBeInTheDocument();
});

test('estado vazio dos artistas sugeridos não interfere no detalhe', async () => {
  authenticateAs('CONTRATANTE');
  apiClient.get.mockImplementation((path) => Promise.resolve(
    path === '/vagas/42'
      ? { ...vaga, propriaDoContratante: true }
      : { content: [] }
  ));

  renderPage();

  expect(await screen.findByText('Ainda não há artistas candidatos para sugerir nesta vaga.')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: vaga.titulo })).toBeInTheDocument();
});

test('erro dos artistas sugeridos fica isolado do detalhe', async () => {
  authenticateAs('CONTRATANTE');
  apiClient.get.mockImplementation((path) => (
    path === '/vagas/42'
      ? Promise.resolve({ ...vaga, propriaDoContratante: true })
      : Promise.reject(new ApiError({ status: 500, message: 'Falha lateral.' }))
  ));

  renderPage();

  expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar os artistas sugeridos');
  expect(screen.getByRole('heading', { name: vaga.titulo })).toBeInTheDocument();
});

test('artista vê somente vagas similares abertas, sem repetir a vaga de origem', async () => {
  authenticateAs('ARTISTA');
  apiClient.get.mockImplementation((path) => {
    if (path === '/vagas/42') return Promise.resolve({ ...vaga, propriaDoContratante: false });
    if (path === '/vagas/42/similares?size=3') {
      return Promise.resolve({
        content: [
          { ...vaga, id: 42, titulo: 'Origem repetida' },
          { ...vaga, id: 43, titulo: 'Vaga aberta similar' },
          { ...vaga, id: 44, titulo: 'Vaga pausada', status: 'PAUSADA' },
        ],
      });
    }
    return Promise.reject(new Error(`GET inesperado: ${path}`));
  });

  renderPage();

  expect(await screen.findByRole('heading', { name: 'Vagas similares' })).toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: 'Vaga aberta similar' })).toBeInTheDocument();
  expect(screen.queryByText('Origem repetida')).not.toBeInTheDocument();
  expect(screen.queryByText('Vaga pausada')).not.toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Artistas sugeridos' })).not.toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Gerenciar oportunidade' })).not.toBeInTheDocument();
  expect(apiClient.get).toHaveBeenCalledWith('/vagas/42/similares?size=3');
});

test('erro de vagas similares fica isolado e preserva a candidatura do artista', async () => {
  authenticateAs('ARTISTA');
  apiClient.get.mockImplementation((path) => (
    path === '/vagas/42'
      ? Promise.resolve({ ...vaga, propriaDoContratante: false })
      : Promise.reject(new ApiError({ status: 500, message: 'Falha lateral.' }))
  ));

  renderPage();

  expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar as vagas similares');
  expect(screen.getByRole('button', { name: 'Candidatar-se' })).toBeInTheDocument();
});

test('estado vazio de vagas similares não interfere no detalhe do artista', async () => {
  authenticateAs('ARTISTA');
  apiClient.get.mockImplementation((path) => Promise.resolve(
    path === '/vagas/42'
      ? { ...vaga, propriaDoContratante: false }
      : { content: [] }
  ));

  renderPage();

  expect(await screen.findByText('Nenhuma vaga similar aberta foi encontrada.')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: vaga.titulo })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Candidatar-se' })).toBeInTheDocument();
});

test('outro contratante vê somente o detalhe público e não consulta candidatos', async () => {
  authenticateAs('CONTRATANTE');
  apiClient.get.mockResolvedValue({ ...vaga, propriaDoContratante: false });

  renderPage();

  expect(await screen.findByRole('heading', { name: vaga.titulo })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Candidatura para artistas' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Gerenciar oportunidade' })).not.toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Artistas sugeridos' })).not.toBeInTheDocument();
  expect(apiClient.get).toHaveBeenCalledTimes(1);
});

test('texto não confiável de candidato é tratado como texto e dados privados não aparecem', async () => {
  authenticateAs('CONTRATANTE');
  const untrustedName = '<img src=x onerror="window.comprometido=true">';
  apiClient.get.mockImplementation((path) => Promise.resolve(
    path === '/vagas/42'
      ? { ...vaga, propriaDoContratante: true }
      : {
          content: [{
            candidaturaId: 1,
            artistaId: 9,
            nomeArtista: untrustedName,
            tagsCoincidentes: [],
            quantidadeTagsCoincidentes: 0,
          }],
        }
  ));

  const { container } = renderPage();

  expect(await screen.findByText(untrustedName)).toBeInTheDocument();
  expect(container.querySelector('img[src="x"]')).not.toBeInTheDocument();
  expect(window.comprometido).toBeUndefined();
});

test.each([
  [401, 'Não autenticado.'],
  [403, 'Acesso negado.'],
  [422, 'Identificador recusado.'],
])('não mascara o erro HTTP %i', async (status, message) => {
  apiClient.get.mockRejectedValue(new ApiError({ status, message, body: null }));
  renderPage(`/vagas/${status}`);

  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(message));
});
