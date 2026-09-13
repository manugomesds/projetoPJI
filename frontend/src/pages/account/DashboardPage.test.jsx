import { render, screen } from '@testing-library/react';
import { getDashboard } from '../../services/account/accountService';
import DashboardPage from './DashboardPage';

jest.mock('../../components/account/AccountLayout', () => function Layout({ children }) { return children; });
jest.mock('../../services/account/accountService', () => ({ getDashboard: jest.fn() }));
jest.mock('../../components/notifications/NotificationPanel', () => function Panel() { return <section aria-label="Notificações RF36" />; });

const availability = { disponivel: true, mensagem: 'Recurso real disponível.' };

const artist = {
  tipoUsuario: 'ARTISTA', nomeExibicao: 'Artista', perfilCompleto: false, avatarUrl: '/avatar.png',
  notificacoes: availability, mensagens: { ...availability, quantidadeNaoLidas: 2 },
  vagasRecomendadas: { content: [{ id: 4, titulo: 'Show de jazz', nomeContratante: 'Casa', cidade: 'Recife', estado: 'PE', remuneraValor: 500, funcoes: [{ id: 1, nome: 'Música' }], quantidadeFuncoesCoincidentes: 1 }], totalElements: 1, hasMore: false },
};

const contractor = {
  tipoUsuario: 'CONTRATANTE', nomeExibicao: 'Produtora', perfilCompleto: true, avatarUrl: '/owner.png',
  notificacoes: availability, mensagens: { ...availability, quantidadeNaoLidas: 0 },
  candidaturasRecentes: { content: [{ id: 8, vagaId: 3, tituloVaga: 'Vaga real', artistaId: 12, nomeArtista: 'Pessoa artista', avatarUrl: '/artist.png', status: 'PENDENTE' }], totalElements: 1, hasMore: false },
  talentosSugeridos: { content: [{ artistaId: 13, nomeExibicao: 'Talento', localizacao: 'SP', avatarUrl: '/talent.png', funcoes: [], quantidadeFuncoesCoincidentes: 2 }], totalElements: 1, hasMore: false },
};

beforeEach(() => getDashboard.mockReset());

test('renderiza dashboard do ARTISTA e aviso de perfil incompleto sem módulos de contratante', async () => {
  getDashboard.mockResolvedValue(artist);
  render(<DashboardPage />);
  expect(screen.getByText('Carregando painel…')).toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: 'Suas oportunidades' })).toBeInTheDocument();
  expect(screen.getByText('Complete seu perfil')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Vagas recomendadas' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Show de jazz' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Candidaturas recentes' })).not.toBeInTheDocument();
  expect(getDashboard).toHaveBeenCalledWith();
  expect(screen.getByRole('region', { name: 'Notificações RF36' })).toBeInTheDocument();
});

test('artista completo não recebe aviso incorreto', async () => {
  getDashboard.mockResolvedValue({ ...artist, perfilCompleto: true });
  render(<DashboardPage />);
  await screen.findByRole('heading', { name: 'Suas oportunidades' });
  expect(screen.queryByText('Complete seu perfil')).not.toBeInTheDocument();
});

test('renderiza módulos exclusivos do CONTRATANTE e dados reais da resposta', async () => {
  getDashboard.mockResolvedValue(contractor);
  render(<DashboardPage />);
  expect(await screen.findByRole('heading', { name: 'Sua produção' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Candidaturas recentes' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Pessoa artista' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Talentos sugeridos' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Talento' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Vagas recomendadas' })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Abrir mensagens' })).toHaveAttribute('href', '/mensagens');
});

test('renderiza estados vazios sem criar contadores falsos', async () => {
  getDashboard.mockResolvedValue({ ...contractor, notificacoes: { disponivel: true, mensagem: 'Sem contador no contrato.' }, mensagens: { disponivel: true, mensagem: 'Também sem contador.' }, candidaturasRecentes: { content: [], totalElements: 0, hasMore: false }, talentosSugeridos: { content: [], totalElements: 0, hasMore: false } });
  render(<DashboardPage />);
  expect(await screen.findByText('Nenhuma candidatura recente em vaga ativa.')).toBeInTheDocument();
  expect(screen.getByText('Nenhum talento compatível no momento.')).toBeInTheDocument();
  expect(screen.queryByLabelText(/não lidas/)).toBeNull();
});

test('preserva texto não confiável como texto e não como HTML', async () => {
  getDashboard.mockResolvedValue({ ...artist, nomeExibicao: '<img src=x onerror=alert(1)>' });
  render(<DashboardPage />);
  expect(await screen.findByText(/<img src=x onerror=alert\(1\)>/)).toBeInTheDocument();
  expect(document.querySelector('img[src="x"]')).toBeNull();
});

test('mostra erro seguro sem vazar mensagem interna da API', async () => {
  getDashboard.mockRejectedValue({ status: 500, message: 'stack SQL secreto' });
  render(<DashboardPage />);
  expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar o painel');
  expect(screen.queryByText('stack SQL secreto')).not.toBeInTheDocument();
});

test('mantém loading enquanto a API não responde', () => {
  getDashboard.mockReturnValue(new Promise(() => {}));
  render(<DashboardPage />);
  expect(screen.getByText('Carregando painel…')).toBeInTheDocument();
});
