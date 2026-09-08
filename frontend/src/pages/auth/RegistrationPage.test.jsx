import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import authService from '../../services/auth/authService';
import RegistrationPage, { calculateAge } from './RegistrationPage';
import { PASSWORD_POLICY_MESSAGE } from '../../utils/passwordPolicy';

jest.mock('../../services/auth/authService', () => ({
  __esModule: true,
  default: { login: jest.fn(), cadastrar: jest.fn() },
}));

function dateYearsAgo(years) {
  const today = new Date();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');
  return `${today.getFullYear() - years}-${month}-${day}`;
}

function renderRegistration() {
  return render(
    <MemoryRouter initialEntries={['/cadastro']} future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
      <Routes>
        <Route path="/cadastro" element={<RegistrationPage />} />
        <Route path="/login" element={<h1>Login após cadastro</h1>} />
      </Routes>
    </MemoryRouter>
  );
}

function fillCommon({ age = 30, role = 'ARTISTA', email = 'pessoa@palco.test' } = {}) {
  fireEvent.change(screen.getByLabelText('Nome completo'), { target: { value: 'Pessoa Teste' } });
  fireEvent.change(screen.getByLabelText('Data de nascimento'), { target: { value: dateYearsAgo(age) } });
  fireEvent.change(screen.getByLabelText('Telefone'), { target: { value: '11999999999' } });
  fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: email } });
  fireEvent.change(screen.getByLabelText('Tipo de usuário'), { target: { value: role } });
  fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'Palco@2026' } });
  fireEvent.change(screen.getByLabelText('Confirme sua senha'), { target: { value: 'Palco@2026' } });
  fireEvent.click(screen.getByRole('checkbox'));
}

beforeEach(() => authService.cadastrar.mockReset());

test('calcula idade respeitando aniversário e rejeita data inválida', () => {
  expect(calculateAge('2010-08-31', new Date(2026, 7, 31))).toBe(16);
  expect(calculateAge('2010-09-01', new Date(2026, 7, 31))).toBe(15);
  expect(calculateAge('2026-02-31', new Date(2026, 7, 31))).toBeNull();
});

test('adulto ARTISTA envia o contrato suportado e segue ao login', async () => {
  authService.cadastrar.mockResolvedValue({ id: 10 });
  renderRegistration();
  fillCommon();

  fireEvent.click(screen.getByRole('button', { name: 'Registrar' }));

  await waitFor(() => expect(authService.cadastrar).toHaveBeenCalledWith(expect.objectContaining({
    nome: 'Pessoa Teste', tipoUsuario: 'ARTISTA', nomeResponsavel: '',
    telefoneResponsavel: '', emailResponsavel: '',
  })));
  expect(await screen.findByRole('heading', { name: 'Login após cadastro' })).toBeInTheDocument();
});

test('contratante usa o mesmo formulário conforme contrato real', async () => {
  authService.cadastrar.mockResolvedValue({ id: 11 });
  renderRegistration();
  fillCommon({ role: 'CONTRATANTE' });
  fireEvent.change(screen.getByLabelText('Tipo de perfil contratante'), { target: { value: 'Pessoa Física' } });

  fireEvent.click(screen.getByRole('button', { name: 'Registrar' }));

  await waitFor(() => expect(authService.cadastrar).toHaveBeenCalledWith(expect.objectContaining({
    tipoUsuario: 'CONTRATANTE', tipoPerfilContratante: 'Pessoa Física',
  })));
});

test('idade inferior a 14 anos é bloqueada sem chamar a API', () => {
  renderRegistration();
  fillCommon({ age: 13 });
  fireEvent.click(screen.getByRole('button', { name: 'Registrar' }));

  expect(authService.cadastrar).not.toHaveBeenCalled();
  expect(screen.getByRole('alert')).toHaveTextContent('idade mínima para cadastro é 14 anos');
});

test('entre 14 e 17 anos exibe e exige os três dados do responsável', () => {
  renderRegistration();
  fillCommon({ age: 17 });
  expect(screen.getByRole('group', { name: 'Responsável legal' })).toBeInTheDocument();

  fireEvent.click(screen.getByRole('button', { name: 'Registrar' }));

  expect(authService.cadastrar).not.toHaveBeenCalled();
  expect(screen.getByRole('alert')).toHaveTextContent('nome, telefone e e-mail do responsável');
});

test('menor de 18 com responsável completo envia os campos oficiais', async () => {
  authService.cadastrar.mockResolvedValue({ id: 12 });
  renderRegistration();
  fillCommon({ age: 17 });
  fireEvent.change(screen.getByLabelText('Nome do responsável'), { target: { value: 'Responsável Teste' } });
  fireEvent.change(screen.getByLabelText('Telefone do responsável'), { target: { value: '11988888888' } });
  fireEvent.change(screen.getByLabelText('E-mail do responsável'), { target: { value: 'responsavel@palco.test' } });

  fireEvent.click(screen.getByRole('button', { name: 'Registrar' }));

  await waitFor(() => expect(authService.cadastrar).toHaveBeenCalledWith(expect.objectContaining({
    nomeResponsavel: 'Responsável Teste', telefoneResponsavel: '11988888888',
    emailResponsavel: 'responsavel@palco.test',
  })));
});

test('a partir de 18 anos não solicita responsável legal', () => {
  renderRegistration();
  fillCommon({ age: 18 });
  expect(screen.queryByRole('group', { name: 'Responsável legal' })).not.toBeInTheDocument();
});

test('campos obrigatórios e aceite de termos são validados', () => {
  renderRegistration();
  expect(screen.getByRole('link', { name: 'Termos de Uso' })).toHaveAttribute('aria-disabled', 'true');
  expect(screen.getByRole('link', { name: 'Termos de Uso' })).not.toHaveAttribute('href');
  expect(screen.getByRole('link', { name: 'Política de Privacidade' })).not.toHaveAttribute('href');
  fireEvent.click(screen.getByRole('button', { name: 'Registrar' }));
  expect(screen.getByRole('alert')).toHaveTextContent('campos obrigatórios');
  expect(authService.cadastrar).not.toHaveBeenCalled();
});

test('senha fora da política mostra a mensagem oficial e bloqueia a API', () => {
  renderRegistration();
  fillCommon();
  fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'artista123' } });
  fireEvent.change(screen.getByLabelText('Confirme sua senha'), { target: { value: 'artista123' } });

  fireEvent.click(screen.getByRole('button', { name: 'Registrar' }));

  expect(screen.getByRole('alert')).toHaveTextContent(PASSWORD_POLICY_MESSAGE);
  expect(authService.cadastrar).not.toHaveBeenCalled();
});

test('email duplicado recebe mensagem contextual sem detalhe interno', async () => {
  authService.cadastrar.mockRejectedValue({ status: 409, message: 'constraint usuarios_email_key' });
  renderRegistration();
  fillCommon();
  fireEvent.click(screen.getByRole('button', { name: 'Registrar' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('Este e-mail já está cadastrado.');
  expect(screen.getByRole('alert')).not.toHaveTextContent('constraint');
});

test('erro técnico é seguro', async () => {
  authService.cadastrar.mockRejectedValue({ status: 500, message: 'stack trace sensível' });
  renderRegistration();
  fillCommon();
  fireEvent.click(screen.getByRole('button', { name: 'Registrar' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível concluir o cadastro.');
  expect(screen.getByRole('alert')).not.toHaveTextContent('stack trace');
});

test('loading evita cadastro duplicado', async () => {
  let resolveRequest;
  authService.cadastrar.mockReturnValue(new Promise((resolve) => { resolveRequest = resolve; }));
  renderRegistration();
  fillCommon();
  const form = screen.getByRole('button', { name: 'Registrar' }).closest('form');

  fireEvent.submit(form);
  fireEvent.submit(form);

  expect(authService.cadastrar).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('button', { name: 'Registrando…' })).toBeDisabled();
  await act(async () => resolveRequest({ id: 13 }));
});
