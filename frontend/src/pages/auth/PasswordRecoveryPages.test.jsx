import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import ApiError from '../../services/api/ApiError';
import apiClient from '../../services/api/apiClient';
import * as passwordRecoveryService from '../../services/auth/passwordRecoveryService';
import ForgotPasswordPage from './ForgotPasswordPage';
import ResetPasswordPage from './ResetPasswordPage';
import { PASSWORD_POLICY_MESSAGE } from '../../utils/passwordPolicy';

jest.mock('../../services/api/apiClient', () => ({
  __esModule: true,
  default: {
    post: jest.fn(),
  },
}));

jest.mock('../../services/auth/passwordRecoveryService', () => {
  const actual = jest.requireActual('../../services/auth/passwordRecoveryService');
  return {
    ...actual,
    redirectToLegacyLogin: jest.fn(),
  };
});

function fillEmail(value = 'artista@palco.test') {
  fireEvent.change(screen.getByLabelText('E-mail'), { target: { value } });
}

function fillPasswords(value = 'Nova@2026') {
  fireEvent.change(screen.getByLabelText('Nova senha'), { target: { value } });
  fireEvent.change(screen.getByLabelText('Confirmar nova senha'), { target: { value } });
}

beforeEach(() => {
  apiClient.post.mockReset();
  passwordRecoveryService.redirectToLegacyLogin.mockReset();
  window.localStorage.clear();
  window.sessionStorage.clear();
  window.history.replaceState(null, '', '/');
});

test('página de solicitação monta formulário acessível de e-mail', () => {
  render(<ForgotPasswordPage />);

  expect(screen.getByRole('heading', { name: 'Recuperar senha' })).toBeInTheDocument();
  expect(screen.getByLabelText('E-mail')).toHaveAttribute('type', 'email');
  expect(screen.getByRole('button', { name: 'Enviar instruções' })).toBeEnabled();
  expect(screen.getByRole('link', { name: 'Palco' })).toHaveAttribute('href', '/');
  expect(screen.getByRole('link', { name: 'Explorar' })).toHaveAttribute('href', '/vagas');
  expect(screen.getByRole('link', { name: 'Comunidade' })).toHaveAttribute('aria-disabled', 'true');
  expect(screen.getByRole('link', { name: 'Comunidade' })).not.toHaveAttribute('href');
  expect(screen.getByRole('link', { name: 'Empresa' })).toHaveAttribute('aria-disabled', 'true');
  expect(screen.getByRole('link', { name: 'Empresa' })).not.toHaveAttribute('href');
});

test('solicitação envia somente o e-mail no body e usa a API pública', async () => {
  apiClient.post.mockResolvedValue({ mensagem: 'Resposta não confiável do servidor.' });
  render(<ForgotPasswordPage />);
  fillEmail();

  fireEvent.click(screen.getByRole('button', { name: 'Enviar instruções' }));

  await waitFor(() =>
    expect(apiClient.post).toHaveBeenCalledWith(
      '/auth/forgot-password',
      { email: 'artista@palco.test' },
      { token: null }
    )
  );
  await waitFor(() =>
    expect(screen.getByRole('status')).toHaveTextContent(
      passwordRecoveryService.GENERIC_RECOVERY_MESSAGE
    )
  );
  expect(screen.queryByText('Resposta não confiável do servidor.')).not.toBeInTheDocument();
});

test.each([
  ['e-mail inexistente', 'Conta não encontrada.'],
  ['conta Google-only', 'Use o Google.'],
  ['conta convencional', 'Link enviado.'],
])('resposta visual permanece genérica para %s', async (scenario, backendMessage) => {
  apiClient.post.mockResolvedValue({ mensagem: backendMessage });
  render(<ForgotPasswordPage />);
  fillEmail(`${scenario.replaceAll(' ', '-')}@palco.test`);

  fireEvent.click(screen.getByRole('button', { name: 'Enviar instruções' }));

  await waitFor(() =>
    expect(screen.getByRole('status')).toHaveTextContent(
      passwordRecoveryService.GENERIC_RECOVERY_MESSAGE
    )
  );
  expect(screen.queryByText(backendMessage)).not.toBeInTheDocument();
});

test('solicitação anuncia loading e bloqueia chamadas duplicadas', async () => {
  let resolveRequest;
  apiClient.post.mockReturnValue(
    new Promise((resolve) => {
      resolveRequest = resolve;
    })
  );
  render(<ForgotPasswordPage />);
  fillEmail();
  const button = screen.getByRole('button', { name: 'Enviar instruções' });

  fireEvent.click(button);
  fireEvent.submit(button.closest('form'));

  expect(apiClient.post).toHaveBeenCalledTimes(1);
  expect(button).toBeDisabled();
  expect(screen.getByRole('status')).toHaveTextContent('Enviando solicitação…');

  await act(async () => resolveRequest({ mensagem: 'ok' }));
});

test('erro técnico da solicitação não expõe a mensagem recebida da API', async () => {
  apiClient.post.mockRejectedValue(
    new ApiError({ status: 500, message: 'Detalhe interno sensível.', body: null })
  );
  render(<ForgotPasswordPage />);
  fillEmail();

  fireEvent.click(screen.getByRole('button', { name: 'Enviar instruções' }));

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Não foi possível enviar a solicitação.'
  );
  expect(screen.getByRole('alert')).not.toHaveTextContent('Detalhe interno sensível.');
});

test('redefinição captura o fragmento, higieniza a URL e envia token e senha no body', async () => {
  const token = 'TOKEN_SEGURO_NAO_RENDERIZADO';
  window.history.replaceState(null, '', `/redefinir-senha#token=${token}`);
  apiClient.post.mockResolvedValue({ mensagem: 'Senha redefinida com sucesso.' });
  render(<ResetPasswordPage />);

  expect(window.location.hash).toBe('');
  expect(document.body).not.toHaveTextContent(token);
  expect(window.localStorage).toHaveLength(0);
  expect(window.sessionStorage).toHaveLength(0);
  fillPasswords();
  fireEvent.click(screen.getByRole('button', { name: 'Redefinir senha' }));

  await waitFor(() =>
    expect(apiClient.post).toHaveBeenCalledWith(
      '/auth/reset-password',
      { token, novaSenha: 'Nova@2026' },
      { token: null }
    )
  );
  expect(apiClient.post.mock.calls[0][0]).not.toContain('?token=');
  await waitFor(() =>
    expect(screen.getByRole('status')).toHaveTextContent('Senha redefinida com sucesso')
  );
  expect(passwordRecoveryService.redirectToLegacyLogin).toHaveBeenCalledTimes(1);
});

test('token apenas em query é descartado e não habilita a redefinição', () => {
  window.history.replaceState(null, '', '/redefinir-senha?token=NA_QUERY');
  render(<ResetPasswordPage />);

  expect(window.location.search).toBe('');
  expect(screen.getByRole('button', { name: 'Redefinir senha' })).toBeDisabled();
  expect(screen.getByRole('alert')).toHaveTextContent('inválido ou incompleto');
  expect(document.body).not.toHaveTextContent('NA_QUERY');
  expect(apiClient.post).not.toHaveBeenCalled();
});

test('ausência de token impede o submit', () => {
  window.history.replaceState(null, '', '/redefinir-senha');
  render(<ResetPasswordPage />);
  fillPasswords();

  fireEvent.submit(screen.getByRole('button', { name: 'Redefinir senha' }).closest('form'));

  expect(apiClient.post).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: 'Redefinir senha' })).toBeDisabled();
});

test('confirmação divergente bloqueia a API e informa o erro', () => {
  window.history.replaceState(null, '', '/redefinir-senha#token=TOKEN_VALIDO');
  render(<ResetPasswordPage />);
  fireEvent.change(screen.getByLabelText('Nova senha'), { target: { value: 'Senha@Um1' } });
  fireEvent.change(screen.getByLabelText('Confirmar nova senha'), {
    target: { value: 'Senha@Dois2' },
  });

  fireEvent.click(screen.getByRole('button', { name: 'Redefinir senha' }));

  expect(apiClient.post).not.toHaveBeenCalled();
  expect(screen.getByRole('alert')).toHaveTextContent('não são iguais');
});

test('senha fora da política mostra a mensagem oficial e não chama a API', () => {
  window.history.replaceState(null, '', '/redefinir-senha#token=TOKEN_VALIDO');
  render(<ResetPasswordPage />);
  fillPasswords('artista123');

  fireEvent.click(screen.getByRole('button', { name: 'Redefinir senha' }));

  expect(screen.getByRole('alert')).toHaveTextContent(PASSWORD_POLICY_MESSAGE);
  expect(apiClient.post).not.toHaveBeenCalled();
});

test('redefinição anuncia loading e evita submit duplicado', async () => {
  let resolveRequest;
  window.history.replaceState(null, '', '/redefinir-senha#token=TOKEN_VALIDO');
  apiClient.post.mockReturnValue(
    new Promise((resolve) => {
      resolveRequest = resolve;
    })
  );
  render(<ResetPasswordPage />);
  fillPasswords();
  const button = screen.getByRole('button', { name: 'Redefinir senha' });

  fireEvent.click(button);
  fireEvent.submit(button.closest('form'));

  expect(apiClient.post).toHaveBeenCalledTimes(1);
  expect(button).toBeDisabled();
  expect(screen.getByRole('status')).toHaveTextContent('Redefinindo senha…');

  await act(async () => resolveRequest({ mensagem: 'ok' }));
});

test.each(['inválido', 'expirado'])(
  'token %s recebe erro uniforme sem revelar o token',
  async () => {
    const token = 'TOKEN_NAO_EXPOSTO';
    window.history.replaceState(null, '', `/redefinir-senha#token=${token}`);
    apiClient.post.mockRejectedValue(
      new ApiError({ status: 404, message: 'Resposta backend variável.', body: null })
    );
    render(<ResetPasswordPage />);
    fillPasswords();

    fireEvent.click(screen.getByRole('button', { name: 'Redefinir senha' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      passwordRecoveryService.INVALID_RESET_LINK_MESSAGE
    );
    expect(document.body).not.toHaveTextContent(token);
    expect(screen.getByRole('alert')).not.toHaveTextContent('Resposta backend variável.');
  }
);

test('erro 500 na redefinição usa mensagem técnica sem conteúdo sensível', async () => {
  window.history.replaceState(null, '', '/redefinir-senha#token=TOKEN_OCULTO');
  apiClient.post.mockRejectedValue(
    new ApiError({ status: 500, message: 'Falha interna com detalhes.', body: null })
  );
  render(<ResetPasswordPage />);
  fillPasswords();

  fireEvent.click(screen.getByRole('button', { name: 'Redefinir senha' }));

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Não foi possível redefinir a senha.'
  );
  expect(screen.getByRole('alert')).not.toHaveTextContent('Falha interna com detalhes.');
  expect(document.body).not.toHaveTextContent('TOKEN_OCULTO');
});

test('campos de nova senha preservam o limite backend de 72 caracteres', () => {
  window.history.replaceState(null, '', '/redefinir-senha#token=TOKEN_VALIDO');
  render(<ResetPasswordPage />);

  expect(screen.getByLabelText('Nova senha')).toHaveAttribute('type', 'password');
  expect(screen.getByLabelText('Nova senha')).toHaveAttribute('maxlength', '72');
  expect(screen.getByLabelText('Confirmar nova senha')).toHaveAttribute('maxlength', '72');
});
