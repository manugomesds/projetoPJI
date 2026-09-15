import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import SavedItemsPage from './SavedItemsPage';
import AuthenticatedOnly from '../../components/account/AuthenticatedOnly';
import * as service from '../../services/salvos/savedService';
import { SESSION_STORAGE_KEY } from '../../auth/sessionService';
jest.mock('../../services/salvos/savedService', () => ({ ...jest.requireActual('../../services/salvos/savedService'),
  getSavedItems: jest.fn(), getSavedState: jest.fn(), removeSavedItem: jest.fn() }));
const item = { tipoAlvo: 'PERFIL_ARTISTA', alvoId: 73, nome: 'Marina', funcoes: 'Canto', localizacao: 'São Paulo/SP', disponivel: true, href: '/perfis/ARTISTA/73' };
const page = (content = [item], extra = {}) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1, hasNext: false, hasPrevious: false, ...extra });
function mount(path='/salvos') { return render(<MemoryRouter initialEntries={[path]} future={{ v7_relativeSplatPath:true,v7_startTransition:true }}><Routes>
  <Route path="/salvos" element={<AuthenticatedOnly><SavedItemsPage /></AuthenticatedOnly>} /><Route path="/login" element={<h1>Login privado</h1>} />
</Routes></MemoryRouter>); }
beforeEach(() => { jest.clearAllMocks(); sessionStorage.clear(); localStorage.clear(); sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ token: 'jwt', tipoUsuario: 'CONTRATANTE' })); service.getSavedItems.mockResolvedValue(page()); service.getSavedState.mockResolvedValue({ salvo:false }); service.removeSavedItem.mockResolvedValue(null); });
test('privada: visitante vai ao login sem listar', () => { sessionStorage.clear(); mount(); expect(screen.getByText('Login privado')).toBeInTheDocument(); expect(service.getSavedItems).not.toHaveBeenCalled(); });
test('lista mostra alvo público e estrela salva sem aba obra', async () => { mount(); expect(await screen.findByRole('heading', {name:'Marina'})).toBeInTheDocument(); expect(screen.getByRole('link', {name:'Abrir perfil'})).toHaveAttribute('href','/perfis/ARTISTA/73'); expect(screen.getByRole('button',{name:/Remover perfil Marina/})).toHaveAttribute('aria-pressed','true'); expect(screen.queryByText('Obras')).not.toBeInTheDocument(); expect(screen.getByText('Funções: Canto')).toBeInTheDocument(); });
test('filtros reiniciam paginação e próxima usa page 1', async () => {
  service.getSavedItems.mockResolvedValue(page([item], {hasNext:true,totalPages:2,totalElements:21})); mount(); await screen.findByRole('heading',{name:'Marina'});
  fireEvent.click(screen.getByRole('button',{name:'Próxima'})); await waitFor(() => expect(service.getSavedItems).toHaveBeenLastCalledWith({tipoAlvo:'',page:1},expect.anything()));
  fireEvent.click(screen.getByRole('button',{name:'Vagas'})); await waitFor(() => expect(service.getSavedItems).toHaveBeenLastCalledWith({tipoAlvo:'VAGA',page:0},expect.anything()));
  fireEvent.click(screen.getByRole('button',{name:'Perfis'})); await waitFor(() => expect(service.getSavedItems).toHaveBeenLastCalledWith({tipoAlvo:'PERFIL_ARTISTA',page:0},expect.anything()));
});
test('remover recarrega coleção e exibe vazio', async () => { service.getSavedItems.mockResolvedValueOnce(page()).mockResolvedValue(page([])); mount(); fireEvent.click(await screen.findByRole('button',{name:/Remover perfil/})); expect(await screen.findByText('Nenhum salvo por aqui ainda')).toBeInTheDocument(); expect(service.removeSavedItem).toHaveBeenCalledWith('PERFIL_ARTISTA',73); });
test('histórico cancelado informa status e não oferece abertura indevida', async () => { service.getSavedItems.mockResolvedValue(page([{tipoAlvo:'VAGA',alvoId:91,nome:'Festival',status:'CANCELADA',disponivel:false,href:null}])); mount(); expect(await screen.findByText('Status: cancelada')).toBeInTheDocument(); expect(screen.queryByRole('link',{name:'Abrir vaga'})).not.toBeInTheDocument(); expect(screen.getByRole('button',{name:/Remover vaga/})).toBeEnabled(); });
test('loading e erro são explícitos', async () => { let reject; service.getSavedItems.mockImplementation(() => new Promise((_,r) => {reject=r;})); mount(); expect(screen.getByRole('status')).toHaveTextContent('Carregando'); await act(async () => reject(new Error('Sem conexão'))); expect(screen.getByRole('alert')).toHaveTextContent('Sem conexão'); });
