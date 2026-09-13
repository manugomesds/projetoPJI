import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import {
  createVacancy,
  getManagedVacancy,
  listVacancyTags,
  updateVacancy,
} from '../../services/vagas/vacancyManagementService';
import VacancyCreatePage from './VacancyCreatePage';
import VacancyEditPage from './VacancyEditPage';

jest.mock('../../components/vagas/ContractorVacancyLayout', () => function Layout({ children }) { return children; });
jest.mock('../../services/vagas/vacancyManagementService', () => ({
  createVacancy: jest.fn(),
  getManagedVacancy: jest.fn(),
  listVacancyTags: jest.fn(),
  updateVacancy: jest.fn(),
}));

const ownVacancy = {
  id: 8, propriaDoContratante: true, titulo: 'Vaga atual', descricao: 'Descrição',
  requisitos: 'Requisitos', areaId: 6, abrangencia: 'LOCAL', valorMinimo: 500, formaRemuneracao: 'POR_EVENTO', cidade: 'Recife',
  estado: 'PE', modeloTrabalho: 'PRESENCIAL', tipoContrato: 'Freela', funcaoIds: [2], status: 'PAUSADA',
};

function renderRoute(path, element) {
  return render(<MemoryRouter initialEntries={[path]} future={{ v7_relativeSplatPath: true, v7_startTransition: true }}><Routes><Route path={path.includes('editar') ? '/vagas/:id/editar' : '/vagas/nova'} element={element} /><Route path="/vagas/:id/gerenciar" element={<h1>Gestão após salvar</h1>} /></Routes></MemoryRouter>);
}

beforeEach(() => {
  createVacancy.mockReset();
  getManagedVacancy.mockReset();
  listVacancyTags.mockReset();
  updateVacancy.mockReset();
  listVacancyTags.mockResolvedValue([{ id: 2, areaId: 6, nome: 'Música' }]);
});

test('publicação aguarda catálogo real de áreas sem enviar vaga', async () => {
  renderRoute('/vagas/nova', <VacancyCreatePage />);
  expect(await screen.findByRole('button', { name: 'Publicar vaga' })).toBeDisabled();
  expect(screen.getByText(/Publicação indisponível/)).toBeInTheDocument();
  expect(createVacancy).not.toHaveBeenCalled();
});

test.each([
  [403, 'Você não tem permissão'],
  [409, 'entrou em conflito'],
  [422, 'Dados de publicação inválidos'],
])('edição preserva erro contextual HTTP %s', async (status, expected) => {
  getManagedVacancy.mockResolvedValue(ownVacancy);
  updateVacancy.mockRejectedValue({ status, message: status === 422 ? 'Dados de publicação inválidos' : 'interno' });
  renderRoute('/vagas/8/editar', <VacancyEditPage />);
  await screen.findByLabelText('Música');
  fireEvent.click(screen.getByRole('button', { name: 'Salvar alterações' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(expected);
});

test('edição própria carrega dados/tags e envia somente alterações do formulário', async () => {
  getManagedVacancy.mockResolvedValue(ownVacancy);
  updateVacancy.mockResolvedValue({ ...ownVacancy, titulo: 'Título atualizado' });
  renderRoute('/vagas/8/editar', <VacancyEditPage />);
  const title = await screen.findByLabelText('Título da vaga');
  expect(title).toHaveValue('Vaga atual');
  expect(screen.getByLabelText('Música')).toBeChecked();
  fireEvent.change(title, { target: { value: 'Título atualizado' } });
  fireEvent.click(screen.getByRole('button', { name: 'Salvar alterações' }));
  await waitFor(() => expect(updateVacancy).toHaveBeenCalledWith('8', expect.objectContaining({ titulo: 'Título atualizado', funcaoIds: [2] })));
  expect(await screen.findByRole('heading', { name: 'Gestão após salvar' })).toBeInTheDocument();
});

test('contratante terceiro não recebe formulário de edição', async () => {
  getManagedVacancy.mockResolvedValue({ ...ownVacancy, propriaDoContratante: false });
  renderRoute('/vagas/8/editar', <VacancyEditPage />);
  expect(await screen.findByRole('heading', { name: 'Acesso negado' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Salvar alterações' })).not.toBeInTheDocument();
});

test('edição preserva 404 sem inventar vaga', async () => {
  getManagedVacancy.mockRejectedValue({ status: 404, message: 'Vaga não encontrada.' });
  renderRoute('/vagas/404/editar', <VacancyEditPage />);
  expect(await screen.findByRole('alert')).toHaveTextContent('A vaga solicitada não foi encontrada');
});
