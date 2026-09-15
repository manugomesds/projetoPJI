import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import SaveButton from './SaveButton';
import { SESSION_STORAGE_KEY } from '../../auth/sessionService';
import * as service from '../../services/salvos/savedService';

jest.mock('../../services/salvos/savedService', () => ({ ...jest.requireActual('../../services/salvos/savedService'),
  getSavedState: jest.fn(), saveItem: jest.fn(), removeSavedItem: jest.fn() }));
const props = { tipoAlvo: 'PERFIL_ARTISTA', alvoId: 73, nome: 'Marina', showCount: true };
beforeEach(() => {
  sessionStorage.clear(); localStorage.clear(); jest.clearAllMocks();
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ token: 'jwt', tipoUsuario: 'CONTRATANTE' }));
  service.getSavedState.mockResolvedValue({ salvo: false, quantidadeSalvos: 2 });
  service.saveItem.mockResolvedValue({ salvo: true, quantidadeSalvos: 3 });
  service.removeSavedItem.mockResolvedValue(null);
});
const button = () => screen.getByRole('button', { name: /Salvar perfil Marina/ });
async function ready() { await waitFor(() => expect(button()).not.toBeDisabled()); }

test('consulta estado real e desenha estrela vazia', async () => {
  render(<SaveButton {...props} />); await ready();
  expect(button()).toHaveAttribute('aria-pressed', 'false');
  expect(button().querySelector('path')).toHaveAttribute('fill', 'none');
  expect(screen.getByText('2 salvamentos')).toBeInTheDocument();
});
test('cancelar confirmação não persiste; foco retorna para estrela', async () => {
  render(<SaveButton {...props} />); await ready(); fireEvent.click(button());
  expect(screen.getByRole('dialog', { name: 'Salvar este perfil?' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
  expect(service.saveItem).not.toHaveBeenCalled(); expect(button()).toHaveFocus();
});
test('confirma chama POST uma vez, preenche estrela e atualiza contador', async () => {
  render(<SaveButton {...props} />); await ready(); fireEvent.click(button());
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar salvamento' }));
  expect(await screen.findByRole('button', { name: 'Remover perfil Marina dos salvos' })).toHaveAttribute('aria-pressed', 'true');
  expect(service.saveItem).toHaveBeenCalledTimes(1); expect(service.saveItem).toHaveBeenCalledWith('PERFIL_ARTISTA', 73);
  expect(screen.getByText('3 salvamentos')).toBeInTheDocument();
});
test('remontagem e pageshow consultam novamente o servidor', async () => {
  const first = render(<SaveButton {...props} />); await ready(); first.unmount();
  service.getSavedState.mockResolvedValue({ salvo: true, quantidadeSalvos: 3 });
  render(<SaveButton {...props} />);
  expect(await screen.findByRole('button', { name: /Remover perfil/ })).toHaveAttribute('aria-pressed', 'true');
  service.getSavedState.mockResolvedValue({ salvo: false, quantidadeSalvos: 2 });
  await act(async () => window.dispatchEvent(new Event('pageshow')));
  await ready(); expect(button()).toHaveAttribute('aria-pressed', 'false');
  expect(service.getSavedState).toHaveBeenCalledTimes(3);
});
test('remove sem confirmação e usa contador retornado pelo servidor', async () => {
  service.getSavedState.mockResolvedValueOnce({ salvo: true, quantidadeSalvos: 3 }).mockResolvedValue({ salvo: false, quantidadeSalvos: 2 });
  render(<SaveButton {...props} />);
  fireEvent.click(await screen.findByRole('button', { name: /Remover perfil/ }));
  await ready(); expect(service.removeSavedItem).toHaveBeenCalledWith('PERFIL_ARTISTA', 73);
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument(); expect(screen.getByText('2 salvamentos')).toBeInTheDocument();
});
test.each([false, true])('falha preserva estado anterior salvo=%s', async saved => {
  service.getSavedState.mockResolvedValue({ salvo: saved, quantidadeSalvos: 2 });
  service.saveItem.mockRejectedValue(new Error('Sem conexão')); service.removeSavedItem.mockRejectedValue(new Error('Sem conexão'));
  render(<SaveButton {...props} />);
  const target = await screen.findByRole('button', { name: saved ? /Remover perfil/ : /Salvar perfil/ });
  await waitFor(() => expect(target).not.toBeDisabled()); fireEvent.click(target);
  if (!saved) fireEvent.click(screen.getByText('Confirmar salvamento'));
  expect(await screen.findByRole('alert')).toHaveTextContent('Sem conexão');
  expect(target).toHaveAttribute('aria-pressed', String(saved));
});
test('visitante recebe link de login, sem consulta ou persistência', () => {
  sessionStorage.clear(); render(<SaveButton {...props} />);
  expect(screen.getByRole('link', { name: 'Entrar para salvar perfil' })).toHaveAttribute('href', '/login');
  expect(service.getSavedState).not.toHaveBeenCalled(); expect(service.saveItem).not.toHaveBeenCalled();
});
test('requisição pendente desabilita confirmações repetidas', async () => {
  let resolve; service.saveItem.mockImplementation(() => new Promise(r => { resolve = r; }));
  render(<SaveButton {...props} />); await ready(); fireEvent.click(button());
  const confirm = screen.getByText('Confirmar salvamento'); fireEvent.click(confirm); fireEvent.click(confirm);
  expect(confirm).toBeDisabled(); expect(service.saveItem).toHaveBeenCalledTimes(1);
  await act(async () => resolve({ salvo: true, quantidadeSalvos: 3 }));
});
test('sincroniza duas estrelas do mesmo alvo por evento', async () => {
  render(<><SaveButton {...props} /><SaveButton {...props} /></>);
  await waitFor(() => expect(screen.getAllByRole('button')[0]).not.toBeDisabled());
  await act(async () => service.publishSavedState('PERFIL_ARTISTA', 73, { salvo: true, quantidadeSalvos: 3 }));
  expect(screen.getAllByRole('button', { name: /Remover perfil/ })).toHaveLength(2);
});
