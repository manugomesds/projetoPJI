import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ApiError from '../../services/api/ApiError';
import apiClient from '../../services/api/apiClient';
import * as publicProfileService from '../../services/perfis/publicProfileService';
import PublicProfilePage from './PublicProfilePage';

jest.mock('../../services/api/apiClient', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const artist = {
  usuarioId: 12,
  nomeExibicao: 'Lia do Palco',
  biografia: 'Cantora e compositora.',
  localizacao: 'Campinas - SP',
  urlPortfolio: 'https://portfolio.example/lia',
  bannerUrl: 'https://cdn.example/banner.jpg',
  avatarUrl: 'https://cdn.example/avatar.jpg',
  funcoes: [
    { id: 1, nome: 'Música' },
    { id: 2, nome: 'Teatro' },
  ],
};

const contractor = {
  usuarioId: 30,
  nomeExibicao: 'Estúdio Aurora',
  nomeEmpresa: 'Estúdio Aurora',
  tipoPerfil: 'Produtora',
  biografia: 'Produções culturais.',
  localizacao: 'São Paulo - SP',
  avatarUrl: 'https://cdn.example/empresa.jpg',
};

function renderPage(path = '/perfis/ARTISTA/12') {
  return render(
    <MemoryRouter
      initialEntries={[path]}
      future={{ v7_relativeSplatPath: true, v7_startTransition: true }}
    >
      <Routes>
        <Route path="/perfis/:tipo/:id" element={<PublicProfilePage />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => {
  apiClient.get.mockReset();
  apiClient.post.mockReset();
  window.localStorage.clear();
  window.sessionStorage.clear();
  document.body.className = '';
  document.title = 'Palco';
});

test('carrega artista anonimamente pela rota e parâmetros normalizados', async () => {
  apiClient.get.mockResolvedValue(artist);
  renderPage('/perfis/artista/12');

  expect(await screen.findByRole('heading', { name: 'Lia do Palco', level: 1 })).toBeInTheDocument();
  expect(apiClient.get).toHaveBeenCalledWith('/perfis/publicos/ARTISTA/12', { token: null });
  expect(screen.getByText('ARTISTA')).toBeInTheDocument();
  expect(document.body).toHaveClass('perfil-publico-pagina');
  expect(screen.getByRole('link', { name: 'Palco — início' })).toHaveAttribute('href', '/login');
});

test('renderiza o contratante e seu tipo de perfil', async () => {
  apiClient.get.mockResolvedValue(contractor);
  renderPage('/perfis/CONTRATANTE/30');

  expect(await screen.findByRole('heading', { name: 'Estúdio Aurora' })).toBeInTheDocument();
  expect(screen.getByText('CONTRATANTE')).toBeInTheDocument();
  expect(screen.getByText('Produtora')).toBeInTheDocument();
  expect(screen.queryByRole('tab', { name: 'Portfólio' })).not.toBeInTheDocument();
});

test('exibe loading enquanto o perfil é carregado', async () => {
  let resolveRequest;
  apiClient.get.mockReturnValue(new Promise((resolve) => { resolveRequest = resolve; }));
  renderPage();

  expect(screen.getByRole('status')).toHaveTextContent('Carregando perfil público...');
  await act(async () => resolveRequest(artist));
  expect(await screen.findByRole('heading', { name: 'Lia do Palco' })).toBeInTheDocument();
});

test('404 usa a mesma resposta pública para inexistente ou menor oculto', async () => {
  apiClient.get.mockRejectedValue(new ApiError({ status: 404, message: 'Perfil publico nao encontrado.' }));
  renderPage();

  expect(await screen.findByRole('heading', { name: 'Perfil não encontrado' })).toBeInTheDocument();
  expect(screen.getByRole('alert')).toHaveTextContent(
    'Este perfil não existe ou não está disponível publicamente.'
  );
  expect(screen.getByRole('link', { name: 'Voltar para a Palco' })).toHaveAttribute('href', '/login');
});

test('erro inesperado recebe estado genérico sem expor detalhes internos', async () => {
  apiClient.get.mockRejectedValue(new ApiError({ status: 500, message: 'stack SQL sigiloso' }));
  renderPage();

  expect(
    await screen.findByRole('heading', { name: 'Não foi possível carregar o perfil' })
  ).toBeInTheDocument();
  expect(screen.getByRole('alert')).toHaveTextContent('Tente novamente em alguns instantes.');
  expect(screen.queryByText(/stack SQL/)).not.toBeInTheDocument();
});

test.each([
  ['/perfis/ADMIN/1', 'tipo inválido'],
  ['/perfis/ARTISTA/abc', 'ID não numérico'],
  ['/perfis/ARTISTA/0', 'ID zero'],
])('%s rejeita %s sem chamar a API', (path) => {
  renderPage(path);

  expect(screen.getByRole('heading', { name: 'Endereço de perfil inválido' })).toBeInTheDocument();
  expect(apiClient.get).not.toHaveBeenCalled();
});

test('campos opcionais ausentes preservam o perfil e o texto padrão', async () => {
  apiClient.get.mockResolvedValue({ usuarioId: 2, nomeExibicao: 'Perfil mínimo' });
  renderPage('/perfis/ARTISTA/2');

  expect(await screen.findByRole('heading', { name: 'Perfil mínimo' })).toBeInTheDocument();
  expect(screen.getByText('Este perfil ainda não adicionou uma apresentação.')).toBeInTheDocument();
  expect(screen.queryByRole('img', { name: /Avatar/ })).not.toBeInTheDocument();
  expect(screen.queryByLabelText('Áreas de atuação')).not.toBeInTheDocument();
});

test('exibe tags e abre a aba do portfólio sem reload nem nova requisição', async () => {
  apiClient.get.mockResolvedValue(artist);
  renderPage();

  await screen.findByRole('heading', { name: 'Lia do Palco' });
  expect(within(screen.getByLabelText('Áreas de atuação')).getByText('Música')).toBeInTheDocument();
  expect(screen.getByText('Teatro')).toBeInTheDocument();

  fireEvent.click(screen.getByRole('tab', { name: 'Portfólio' }));

  expect(screen.getByRole('tab', { name: 'Portfólio' })).toHaveAttribute('aria-selected', 'true');
  expect(screen.getByRole('link', { name: 'Acessar portfólio' })).toHaveAttribute(
    'href',
    'https://portfolio.example/lia'
  );
  expect(screen.queryByText('Cantora e compositora.')).not.toBeVisible();
  expect(apiClient.get).toHaveBeenCalledTimes(1);
});

test('URLs não HTTP são descartadas', async () => {
  apiClient.get.mockResolvedValue({
    ...artist,
    avatarUrl: 'javascript:alert(1)',
    bannerUrl: 'data:text/html,ataque',
    urlPortfolio: 'javascript:alert(2)',
  });
  renderPage();

  await screen.findByRole('heading', { name: 'Lia do Palco' });
  expect(screen.queryByRole('tab', { name: 'Portfólio' })).not.toBeInTheDocument();
  expect(screen.queryByRole('img', { name: /Lia do Palco/ })).not.toBeInTheDocument();
});

test('conteúdo malicioso permanece texto e dados privados nunca são renderizados', async () => {
  const malicious = '<script>window.comprometido=true</script> & Música';
  apiClient.get.mockResolvedValue({
    ...artist,
    biografia: malicious,
    email: 'privado@example.com',
    telefone: '11999999999',
    dataNascimento: '2010-01-01',
    senha: 'hash-privado',
    googleId: 'google-privado',
    tokenRecuperacao: 'token-privado',
    nomeResponsavel: 'Responsável privado',
    endereco: 'Rua privada',
  });
  const { container } = renderPage();

  expect(await screen.findByText(malicious)).toBeInTheDocument();
  expect(container.querySelector('script')).not.toBeInTheDocument();
  expect(window.comprometido).toBeUndefined();
  [
    'privado@example.com', '11999999999', '2010-01-01', 'hash-privado',
    'google-privado', 'token-privado', 'Responsável privado', 'Rua privada',
  ].forEach((privateValue) => expect(screen.queryByText(privateValue)).not.toBeInTheDocument());
});

test('visitante anônimo visualiza o perfil sem receber ação de chat', async () => {
  apiClient.get.mockResolvedValue(artist);
  renderPage();

  await screen.findByRole('heading', { name: 'Lia do Palco' });
  expect(screen.queryByRole('button', { name: 'Enviar mensagem' })).not.toBeInTheDocument();
});

test('sessão de tipo oposto inicia a conversa e abre o destino React', async () => {
  window.sessionStorage.setItem(
    'palco.sessao',
    JSON.stringify({ token: 'jwt-valido', tipoUsuario: 'CONTRATANTE' })
  );
  apiClient.get.mockResolvedValue(artist);
  apiClient.post.mockResolvedValue({ salaId: 77 });
  const navigation = jest.spyOn(publicProfileService, 'navigateToMessages').mockImplementation(() => {});
  renderPage();

  fireEvent.click(await screen.findByRole('button', { name: 'Enviar mensagem' }));

  await waitFor(() => expect(apiClient.post).toHaveBeenCalledWith('/chat/salas', { usuarioDestinoId: 12 }));
  expect(navigation).toHaveBeenCalledWith(77);
  expect(publicProfileService.messagesUrl(77)).toBe('/mensagens?sala=77');
  navigation.mockRestore();
});

test('preserva a regra contextual de menor retornada pelo backend', async () => {
  window.sessionStorage.setItem(
    'palco.sessao',
    JSON.stringify({ token: 'jwt-valido', tipoUsuario: 'CONTRATANTE' })
  );
  apiClient.get.mockResolvedValue(artist);
  apiClient.post.mockRejectedValue(new ApiError({
    status: 422,
    message: 'Chat com menor exige interacao profissional valida entre os participantes.',
  }));
  renderPage();

  const button = await screen.findByRole('button', { name: 'Enviar mensagem' });
  fireEvent.click(button);

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Chat com menor exige interacao profissional valida entre os participantes.'
  );
  expect(button).toBeEnabled();
});

test('sessão do mesmo tipo não pode iniciar conversa consigo mesma', async () => {
  window.localStorage.setItem(
    'palco.sessao',
    JSON.stringify({ token: 'jwt-valido', tipoUsuario: 'ARTISTA' })
  );
  apiClient.get.mockResolvedValue(artist);
  renderPage();

  await screen.findByRole('heading', { name: 'Lia do Palco' });
  expect(screen.queryByRole('button', { name: 'Enviar mensagem' })).not.toBeInTheDocument();
});

test('falha do chat exibe a mensagem e reabilita o botão', async () => {
  window.sessionStorage.setItem(
    'palco.sessao',
    JSON.stringify({ token: 'jwt-valido', tipoUsuario: 'CONTRATANTE' })
  );
  apiClient.get.mockResolvedValue(artist);
  apiClient.post.mockRejectedValue(new ApiError({ status: 403, message: 'Conversa indisponível.' }));
  renderPage();

  const button = await screen.findByRole('button', { name: 'Enviar mensagem' });
  fireEvent.click(button);

  expect(await screen.findByRole('alert')).toHaveTextContent('Conversa indisponível.');
  expect(button).toBeEnabled();
});
