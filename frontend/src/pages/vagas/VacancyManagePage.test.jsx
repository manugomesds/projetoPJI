import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import {
  cancelVacancy,
  changeVacancyStatus,
  getManagedVacancy,
  getRelatedVacancies,
} from '../../services/vagas/vacancyManagementService';
import VacancyManagePage from './VacancyManagePage';
import { getSuggestedArtists } from '../../services/vagas/vagaService';

jest.mock('../../services/vagas/vagaService', () => ({ getSuggestedArtists: jest.fn() }));

jest.mock('../../components/vagas/ContractorVacancyLayout', () => function Layout({ children }) { return children; });
jest.mock('../../services/vagas/vacancyManagementService', () => ({
  cancelVacancy: jest.fn(),
  changeVacancyStatus: jest.fn(),
  getManagedVacancy: jest.fn(),
  getRelatedVacancies: jest.fn(),
}));

const vacancy = {
  id: 5, propriaDoContratante: true, titulo: 'Vaga de música', status: 'ABERTA',
  nomeContratante: 'Produtora', categoria: 'Música', tipoContrato: 'Freela',
  cidade: 'Recife', estado: 'PE', modeloTrabalho: 'PRESENCIAL', remuneraValor: 900,
  formaPagamento: 'Pix', descricao: 'Descrição segura', requisitos: 'Requisitos', tagIds: [2],
};

function renderManage(path = '/vagas/5/gerenciar') {
  return render(<MemoryRouter initialEntries={[path]} future={{ v7_relativeSplatPath: true, v7_startTransition: true }}><Routes><Route path="/vagas/:id/gerenciar" element={<VacancyManagePage />} /></Routes></MemoryRouter>);
}

beforeEach(() => {
  getSuggestedArtists.mockReset().mockResolvedValue({ content: [], hasNext: false });
  cancelVacancy.mockReset();
  changeVacancyStatus.mockReset();
  getManagedVacancy.mockReset();
  getRelatedVacancies.mockReset();
  getManagedVacancy.mockResolvedValue(vacancy);
  getRelatedVacancies.mockResolvedValue({ content: [] });
});

test('não proprietário não carrega nem recebe controles de análise', async () => {
  getManagedVacancy.mockResolvedValue({ ...vacancy, propriaDoContratante: false });
  renderManage();
  expect(await screen.findByRole('heading', { name: 'Acesso negado' })).toBeInTheDocument();
  expect(getSuggestedArtists).not.toHaveBeenCalled();
  expect(screen.queryByRole('heading', { name: 'Candidaturas recebidas' })).not.toBeInTheDocument();
});

test('ABERTA permite suspender e atualiza a matriz para PAUSADA/reabrir', async () => {
  changeVacancyStatus.mockResolvedValue({ ...vacancy, status: 'PAUSADA' });
  renderManage();
  fireEvent.click(await screen.findByRole('button', { name: 'Suspender vaga' }));
  await waitFor(() => expect(changeVacancyStatus).toHaveBeenCalledWith('5', 'SUSPENDER'));
  expect(await screen.findByText('Pausada')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Reabrir vaga' })).toBeInTheDocument();
});

test.each([
  ['PAUSADA', 'Reabrir vaga', 'REABRIR', 'ABERTA', 'Aberta'],
  ['ABERTA', 'Encerrar vaga', 'ENCERRAR', 'ENCERRADA', 'Encerrada'],
])('executa transição válida a partir de %s', async (status, button, action, nextStatus, label) => {
  getManagedVacancy.mockResolvedValue({ ...vacancy, status });
  changeVacancyStatus.mockResolvedValue({ ...vacancy, status: nextStatus });
  renderManage();
  fireEvent.click(await screen.findByRole('button', { name: button }));
  await waitFor(() => expect(changeVacancyStatus).toHaveBeenCalledWith('5', action));
  expect(await screen.findByText(label)).toBeInTheDocument();
});

test('bloqueia duplo clique em transição', async () => {
  let resolveAction;
  changeVacancyStatus.mockReturnValue(new Promise((resolve) => { resolveAction = resolve; }));
  renderManage();
  const button = await screen.findByRole('button', { name: 'Suspender vaga' });
  fireEvent.click(button);
  fireEvent.click(button);
  expect(changeVacancyStatus).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('button', { name: 'Processando…' })).toBeDisabled();
  await act(async () => resolveAction({ ...vacancy, status: 'PAUSADA' }));
});

test('transição inválida 422 preserva mensagem do contrato', async () => {
  changeVacancyStatus.mockRejectedValue({ status: 422, message: 'Transição inválida para o estado atual.' });
  renderManage();
  fireEvent.click(await screen.findByRole('button', { name: 'Suspender vaga' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Transição inválida para o estado atual');
  expect(screen.getByText('Aberta')).toBeInTheDocument();
});

test('cancelamento exige confirmação e motivo, envia contrato e mostra estado final', async () => {
  cancelVacancy.mockResolvedValue(null);
  renderManage();
  fireEvent.click(await screen.findByRole('button', { name: 'Cancelar vaga' }));
  const dialog = screen.getByRole('dialog');
  fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar vaga' }));
  expect(within(dialog).getByRole('alert')).toHaveTextContent('Confirme');
  fireEvent.click(within(dialog).getByLabelText('Confirmo o cancelamento desta vaga.'));
  fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar vaga' }));
  expect(within(dialog).getByRole('alert')).toHaveTextContent('Informe o motivo');
  fireEvent.change(within(dialog).getByLabelText('Motivo do cancelamento'), { target: { value: 'Projeto adiado' } });
  fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar vaga' }));
  await waitFor(() => expect(cancelVacancy).toHaveBeenCalledWith('5', 'Projeto adiado'));
  expect(await screen.findByText('Cancelada')).toBeInTheDocument();
  expect(screen.getByText(/estado final/)).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Cancelar vaga' })).not.toBeInTheDocument();
});

test('erro 403 no cancelamento permanece contextual e mantém modal aberto', async () => {
  cancelVacancy.mockRejectedValue({ status: 403, message: 'interno' });
  renderManage();
  fireEvent.click(await screen.findByRole('button', { name: 'Cancelar vaga' }));
  const dialog = screen.getByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText('Motivo do cancelamento'), { target: { value: 'Motivo' } });
  fireEvent.click(within(dialog).getByLabelText('Confirmo o cancelamento desta vaga.'));
  fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar vaga' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Você não tem permissão');
  expect(screen.getByRole('dialog')).toBeInTheDocument();
});

test('contratante terceiro é bloqueado antes de renderizar ações', async () => {
  getManagedVacancy.mockResolvedValue({ ...vacancy, propriaDoContratante: false });
  renderManage();
  expect(await screen.findByRole('heading', { name: 'Acesso negado' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Suspender vaga' })).not.toBeInTheDocument();
});

test('404 não inventa estado de gestão', async () => {
  getManagedVacancy.mockRejectedValue({ status: 404, message: 'interno' });
  renderManage('/vagas/999/gerenciar');
  expect(await screen.findByRole('alert')).toHaveTextContent('A vaga solicitada não foi encontrada');
});
