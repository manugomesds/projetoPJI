import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import sessionService from '../../auth/sessionService';
import { getPrivateProfile, updatePrivateProfile } from '../../services/account/accountService';
import ProfilePage from './ProfilePage';
import { PASSWORD_POLICY_MESSAGE } from '../../utils/passwordPolicy';

jest.mock('../../components/account/AccountLayout', () => function Layout({ children }) { return children; });
jest.mock('../../services/account/accountService', () => ({ getPrivateProfile: jest.fn(), updatePrivateProfile: jest.fn() }));

const userArtist = { id: 7, nome: 'Artista', dataNascimento: '1990-01-01', telefone: '1199', email: 'artista@test', tipoUsuario: 'ARTISTA', perfilCompleto: false };
const artistResult = {
  usuario: userArtist,
  perfil: { usuarioId: 7, biografia: 'Bio', localizacao: 'Recife', urlPortfolio: 'https://portfolio.test', bannerUrl: '', avatarUrl: '/avatar.png', tagIds: [1] },
  tags: [{ id: 1, nome: 'Música' }, { id: 2, nome: 'Teatro' }],
};
const contractorResult = {
  usuario: { ...userArtist, id: 8, nome: 'Contratante', email: 'owner@test', tipoUsuario: 'CONTRATANTE', perfilCompleto: true },
  perfil: { usuarioId: 8, nomeEmpresa: 'Produtora', tipoPerfil: 'Empresa', biografia: 'Bio owner', localizacao: 'SP', bannerUrl: '', avatarUrl: '/owner.png' },
  tags: [],
};

function renderPage() {
  return render(<MemoryRouter initialEntries={['/perfil']} future={{ v7_relativeSplatPath: true, v7_startTransition: true }}><Routes><Route path="/perfil" element={<ProfilePage />} /><Route path="/login" element={<h1>Login</h1>} /></Routes></MemoryRouter>);
}

beforeEach(() => {
  getPrivateProfile.mockReset();
  updatePrivateProfile.mockReset();
  window.sessionStorage.clear();
  window.localStorage.clear();
  sessionService.saveSession({ token: 'jwt', id: 999, email: 'storage@test', tipoUsuario: 'CONTRATANTE', senha: 'não' });
});

test('carrega perfil ARTISTA próprio, tags e progresso sem campos de contratante', async () => {
  getPrivateProfile.mockResolvedValue(artistResult);
  renderPage();
  expect(screen.getByText('Carregando perfil…')).toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: 'Meu perfil' })).toBeInTheDocument();
  expect(screen.getByLabelText('Nome')).toHaveValue('Artista');
  expect(screen.getByLabelText('URL do portfólio')).toHaveValue('https://portfolio.test');
  expect(screen.getByLabelText('Música')).toBeChecked();
  expect(screen.getByText('Perfil incompleto')).toBeInTheDocument();
  expect(screen.queryByLabelText('Nome da empresa')).not.toBeInTheDocument();
  expect(document.querySelector('input[name="usuarioId"]')).toBeNull();
});

test('carrega campos próprios do CONTRATANTE sem controles de artista', async () => {
  getPrivateProfile.mockResolvedValue(contractorResult);
  renderPage();
  expect(await screen.findByLabelText('Nome da empresa')).toHaveValue('Produtora');
  expect(screen.getByLabelText('Tipo de perfil')).toHaveValue('Empresa');
  expect(screen.getByText('Perfil completo')).toBeInTheDocument();
  expect(screen.queryByLabelText('URL do portfólio')).not.toBeInTheDocument();
  expect(screen.queryByText('Áreas de atuação')).not.toBeInTheDocument();
});

test('envia atualização válida usando usuário retornado por /me, nunca ID do storage', async () => {
  getPrivateProfile.mockResolvedValue(artistResult);
  updatePrivateProfile.mockResolvedValue({ ...userArtist, nome: 'Artista Atualizada', perfilCompleto: true });
  renderPage();
  const name = await screen.findByLabelText('Nome');
  fireEvent.change(name, { target: { value: 'Artista Atualizada' } });
  fireEvent.click(screen.getByLabelText('Teatro'));
  fireEvent.click(screen.getByRole('button', { name: 'Salvar perfil' }));
  await waitFor(() => expect(updatePrivateProfile).toHaveBeenCalledTimes(1));
  expect(updatePrivateProfile.mock.calls[0][0].id).toBe(7);
  expect(updatePrivateProfile.mock.calls[0][1].tagIds).toEqual([1, 2]);
  expect(await screen.findByRole('status')).toHaveTextContent('Perfil atualizado com sucesso');
});

test('senha exige atual, é limpa após sucesso e nunca é persistida', async () => {
  getPrivateProfile.mockResolvedValue(artistResult);
  updatePrivateProfile.mockResolvedValue({ ...userArtist, perfilCompleto: true });
  renderPage();
  await screen.findByLabelText('Nova senha');
  fireEvent.change(screen.getByLabelText('Nova senha'), { target: { value: 'NovaSenha123!' } });
  fireEvent.click(screen.getByRole('button', { name: 'Salvar perfil' }));
  expect(screen.getByRole('alert')).toHaveTextContent('Informe a senha atual');
  expect(updatePrivateProfile).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('Senha atual'), { target: { value: 'Atual123!' } });
  fireEvent.click(screen.getByRole('button', { name: 'Salvar perfil' }));
  await waitFor(() => expect(updatePrivateProfile).toHaveBeenCalledTimes(1));
  await screen.findByRole('status');
  expect(screen.getByLabelText('Senha atual')).toHaveValue('');
  expect(screen.getByLabelText('Nova senha')).toHaveValue('');
  expect(window.sessionStorage.getItem('palco.sessao')).not.toContain('Atual123!');
  expect(window.sessionStorage.getItem('palco.sessao')).not.toContain('NovaSenha123!');
});

test('nova senha inválida mostra a política e não envia a alteração', async () => {
  getPrivateProfile.mockResolvedValue(artistResult);
  renderPage();
  await screen.findByLabelText('Nova senha');
  fireEvent.change(screen.getByLabelText('Senha atual'), { target: { value: 'Atual123!' } });
  fireEvent.change(screen.getByLabelText('Nova senha'), { target: { value: 'artista123' } });

  fireEvent.click(screen.getByRole('button', { name: 'Salvar perfil' }));

  expect(screen.getByRole('alert')).toHaveTextContent(PASSWORD_POLICY_MESSAGE);
  expect(updatePrivateProfile).not.toHaveBeenCalled();
});

test('erro contextual mantém formulário utilizável e remove mensagem interna 500', async () => {
  getPrivateProfile.mockResolvedValue(artistResult);
  updatePrivateProfile.mockRejectedValue({ status: 500, message: 'stack privado' });
  renderPage();
  await screen.findByLabelText('Nome');
  fireEvent.click(screen.getByRole('button', { name: 'Salvar perfil' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível atualizar o perfil');
  expect(screen.queryByText('stack privado')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Salvar perfil' })).toBeEnabled();
});

test('erro 403 é preservado de forma contextual', async () => {
  getPrivateProfile.mockResolvedValue(artistResult);
  updatePrivateProfile.mockRejectedValue({ status: 403, message: 'detalhe interno' });
  renderPage();
  await screen.findByLabelText('Nome');
  fireEvent.click(screen.getByRole('button', { name: 'Salvar perfil' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('senha atual está incorreta ou você não tem permissão');
});

test('email alterado limpa sessão e exige novo login', async () => {
  getPrivateProfile.mockResolvedValue(artistResult);
  updatePrivateProfile.mockResolvedValue({ ...userArtist, email: 'novo@test' });
  renderPage();
  await screen.findByLabelText('E-mail');
  fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'novo@test' } });
  fireEvent.click(screen.getByRole('button', { name: 'Salvar perfil' }));
  expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument();
  expect(sessionService.getSession()).toBeNull();
});

test('falha de carga não renderiza dados nem formulário de outro usuário', async () => {
  getPrivateProfile.mockRejectedValue({ status: 403 });
  renderPage();
  expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar seu perfil');
  expect(screen.queryByRole('button', { name: 'Salvar perfil' })).not.toBeInTheDocument();
});

test('bloqueia reenvio evidente enquanto a atualização está pendente', async () => {
  let resolve;
  getPrivateProfile.mockResolvedValue(artistResult);
  updatePrivateProfile.mockReturnValue(new Promise((done) => { resolve = done; }));
  renderPage();
  await screen.findByLabelText('Nome');
  const save = screen.getByRole('button', { name: 'Salvar perfil' });
  fireEvent.click(save);
  fireEvent.click(save);
  expect(updatePrivateProfile).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('button', { name: 'Salvando…' })).toBeDisabled();
  resolve({ ...userArtist });
  await screen.findByRole('status');
});
