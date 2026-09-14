import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { SESSION_STORAGE_KEY } from '../../auth/sessionService';
import authService from '../../services/auth/authService';
import LoginPage from './LoginPage';

jest.mock('../../services/auth/authService', () => ({
  __esModule: true,
  default: { login: jest.fn(), cadastrar: jest.fn() },
}));

function renderLogin(options = {}) {
  return render(
    <MemoryRouter initialEntries={[options.path || '/login']} future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
      <LoginPage onAuthenticated={options.onAuthenticated || jest.fn()} />
    </MemoryRouter>
  );
}

function fillLogin(email = 'artista@palco.test', senha = 'senha123') {
  fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: email } });
  fireEvent.change(screen.getByLabelText('Senha'), { target: { value: senha } });
}

beforeEach(() => {
  authService.login.mockReset();
  window.sessionStorage.clear();
  window.localStorage.clear();
});

test('renderiza somente o login convencional com e-mail e senha', () => {
  renderLogin();
  expect(screen.getByRole('heading', { name: /Bem-vindo\(a\) a Palco/i })).toBeInTheDocument();
  expect(screen.getByLabelText('E-mail')).toHaveAttribute('type', 'email');
  expect(screen.getByLabelText('Senha')).toHaveAttribute('type', 'password');
  expect(screen.queryByRole('button', { name: /google/i })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Artistas' })).toHaveAttribute('aria-disabled', 'true');
  expect(screen.getByRole('link', { name: 'Sobre nós' })).toHaveAttribute('href', '/#sobre');
  expect(screen.getByRole('link', { name: 'Acessar com Google' })).toHaveAttribute('href', '/google-callback.html');
  expect(screen.getByRole('link', { name: 'Termos de Serviço do Palco' })).not.toHaveAttribute('href');
  expect(screen.getByRole('link', { name: 'Política de Privacidade' })).not.toHaveAttribute('href');
});

test('envia payload, salva sessão JWT sem senha e segue ao painel legado atual', async () => {
  const onAuthenticated = jest.fn();
  authService.login.mockResolvedValue({ token: 'jwt-login', id: 8, email: 'artista@palco.test', tipoUsuario: 'ARTISTA', perfilCompleto: false });
  renderLogin({ onAuthenticated });
  fillLogin();

  fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));

  await waitFor(() => expect(authService.login).toHaveBeenCalledWith({ email: 'artista@palco.test', senha: 'senha123' }));
  await waitFor(() => expect(onAuthenticated).toHaveBeenCalledTimes(1));
  const stored = window.sessionStorage.getItem(SESSION_STORAGE_KEY);
  expect(stored).toContain('jwt-login');
  expect(stored).not.toContain('senha123');
  expect(window.localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
});

test('credenciais inválidas exibem mensagem genérica sem detalhe do backend', async () => {
  authService.login.mockRejectedValue({ status: 401, message: 'Usuário interno 77 falhou.' });
  renderLogin();
  fillLogin();
  fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha incorretos.');
  expect(screen.getByRole('alert')).not.toHaveTextContent('Usuário interno');
  expect(window.sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
});

test('erro técnico não expõe detalhes internos', async () => {
  authService.login.mockRejectedValue({ status: 500, message: 'stack trace sensível' });
  renderLogin();
  fillLogin();
  fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível entrar agora.');
  expect(screen.getByRole('alert')).not.toHaveTextContent('stack trace');
});

test('loading bloqueia duplo submit', async () => {
  let resolveRequest;
  authService.login.mockReturnValue(new Promise((resolve) => { resolveRequest = resolve; }));
  renderLogin();
  fillLogin();
  const form = screen.getByRole('button', { name: 'Entrar' }).closest('form');

  fireEvent.submit(form);
  fireEvent.submit(form);

  expect(authService.login).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('button', { name: 'Entrando…' })).toBeDisabled();
  await act(async () => resolveRequest({ token: 'jwt', tipoUsuario: 'CONTRATANTE' }));
});

test('campos vazios são bloqueados no frontend', () => {
  renderLogin();
  fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));
  expect(authService.login).not.toHaveBeenCalled();
  expect(screen.getByRole('alert')).toHaveTextContent('Preencha e-mail e senha');
});

test('retorno do cadastro é anunciado sem alert', () => {
  renderLogin({ path: '/login?cadastro=sucesso' });
  expect(screen.getByRole('status')).toHaveTextContent('Cadastro realizado com sucesso');
});
