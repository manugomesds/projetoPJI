import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import * as api from '../../services/portfolio/portfolioService';
import PortfolioSection from './PortfolioSection';

jest.mock('../../services/portfolio/portfolioService', () => ({ ...jest.requireActual('../../services/portfolio/portfolioService'),
  listPortfolio: jest.fn(), uploadFile: jest.fn(), addVideo: jest.fn(), deleteItem: jest.fn(), privateContent: jest.fn() }));
const empty = { content: [], page: 0, size: 20, hasMore: false, totalElements: 0 };
const image = { id: 1, nomeOriginal: 'obra.png', tipo: 'IMAGEM', tipoMime: 'image/png', tamanhoBytes: 50 };
const pdf = { id: 2, nomeOriginal: 'obra.pdf', tipo: 'PDF', tipoMime: 'application/pdf', tamanhoBytes: 50 };
const audio = { id: 3, nomeOriginal: 'obra.mp3', tipo: 'AUDIO', tipoMime: 'audio/mpeg', tamanhoBytes: 50 };
function lists(files = [], videos = []) { api.listPortfolio.mockImplementation((owner, artist, kind) => Promise.resolve({ ...empty, content: kind === 'arquivos' ? files : videos })); }
beforeEach(() => { jest.clearAllMocks(); lists(); URL.createObjectURL = jest.fn(() => 'blob:portfolio'); URL.revokeObjectURL = jest.fn(); api.privateContent.mockResolvedValue(new Blob(['bytes'])); });
async function loaded() { await waitFor(() => expect(screen.queryByText('Carregando portfólio…')).not.toBeInTheDocument()); }
function select(file = new File(['png'], 'obra.png', { type: 'image/png' })) { fireEvent.change(screen.getByLabelText('Arquivo do portfólio'), { target: { files: [file] } }); }

test('ARTISTA vê gerenciador acessível e limites reais', async () => {
  render(<PortfolioSection owner />); await loaded();
  expect(screen.getByLabelText('Arquivo do portfólio')).toHaveAttribute('accept', '.jpg,.jpeg,.png,.pdf,.mp3');
  expect(screen.getByText(/JPG\/JPEG\/PNG até 5 MB/)).toHaveTextContent('PDF até 10 MB; MP3 até 20 MB');
  expect(screen.getByRole('button', { name: 'Enviar arquivo' })).toBeDisabled();
});
test('público não recebe upload nem ações de proprietário', async () => {
  lists([image]); render(<PortfolioSection artistId={42} />); await loaded();
  expect(screen.queryByLabelText('Arquivo do portfólio')).not.toBeInTheDocument(); expect(screen.queryByText('Excluir arquivo')).not.toBeInTheDocument();
  expect(api.listPortfolio).toHaveBeenCalledWith(false, 42, 'arquivos', 0, expect.any(AbortSignal));
});
test.each(['evil.docx', 'video.mp4'])('bloqueia formato %s antes do upload', async name => {
  render(<PortfolioSection owner />); await loaded(); select(new File(['file'], name));
  expect(screen.getByRole('alert')).toHaveTextContent('Formato não permitido'); expect(api.uploadFile).not.toHaveBeenCalled();
});
test('bloqueia oversize e associa erro ao campo', async () => {
  render(<PortfolioSection owner />); await loaded(); select({ name: 'grande.png', size: 5 * 1024 * 1024 + 1, type: 'image/png' });
  expect(screen.getByRole('alert')).toHaveTextContent('limite de 5 MB'); expect(screen.getByLabelText('Arquivo do portfólio')).toHaveAttribute('aria-describedby', expect.stringContaining('portfolio-error'));
});
test('loading, sucesso, FormData por serviço e atualização da listagem', async () => {
  let resolve; api.uploadFile.mockReturnValue(new Promise(r => { resolve = r; })); render(<PortfolioSection owner />); await loaded(); select();
  fireEvent.click(screen.getByRole('button', { name: 'Enviar arquivo' }));
  expect(screen.getByRole('button', { name: 'Enviando…' })).toBeDisabled(); expect(api.uploadFile).toHaveBeenCalledTimes(1);
  lists([image]); await act(async () => { resolve({ id: 1 }); });
  expect(await screen.findByText('Arquivo enviado com sucesso.')).toBeInTheDocument(); expect(await screen.findByText('obra.png')).toBeInTheDocument();
  expect(api.listPortfolio.mock.calls.filter(c => c[2] === 'arquivos')).toHaveLength(2);
});
test.each([422, 413, 500])('mostra erro real %s sem simular sucesso', async status => {
  api.uploadFile.mockRejectedValue({ status, message: `Erro real ${status}` }); render(<PortfolioSection owner />); await loaded(); select();
  fireEvent.click(screen.getByRole('button', { name: 'Enviar arquivo' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(`Erro real ${status}`); expect(screen.queryByText('Arquivo enviado com sucesso.')).not.toBeInTheDocument();
});
test('carrega prévia privada com blob autenticado e revoga URL ao sair', async () => {
  lists([image]); const { unmount } = render(<PortfolioSection owner />); await loaded();
  fireEvent.click(screen.getByRole('button', { name: 'Carregar prévia' }));
  expect(await screen.findByRole('img', { name: 'obra.png' })).toHaveAttribute('src', 'blob:portfolio');
  expect(api.privateContent).toHaveBeenCalledWith(1, expect.any(AbortSignal)); unmount(); expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:portfolio');
});
test('imagem pública, PDF seguro e controles MP3 sem token na URL', async () => {
  lists([image, pdf, audio]); render(<PortfolioSection artistId={42} />); await loaded();
  expect(screen.getByRole('img', { name: 'obra.png' })).toHaveAttribute('src', 'http://localhost:8080/api/portfolio/publico/arquivos/1/conteudo');
  expect(screen.getByRole('link', { name: 'Baixar PDF: obra.pdf' })).toHaveAttribute('rel', 'noopener noreferrer');
  expect(screen.getByLabelText('Áudio obra.mp3')).toHaveAttribute('controls'); expect(api.privateContent).not.toHaveBeenCalled();
});
test('PDF privado é preparado autenticado antes do download', async () => {
  lists([pdf]); render(<PortfolioSection owner />); await loaded(); fireEvent.click(screen.getByRole('button', { name: 'Preparar download' }));
  expect(await screen.findByRole('link', { name: 'Baixar PDF: obra.pdf' })).toHaveAttribute('download', 'obra.pdf');
});
test('paginação envia página seguinte e preserva limite', async () => {
  api.listPortfolio.mockResolvedValue({ ...empty, content: [image], hasMore: true }); render(<PortfolioSection owner />); await loaded();
  fireEvent.click(within(screen.getByRole('navigation', { name: 'Paginação de arquivos' })).getByText('Próxima')); await loaded();
  expect(api.listPortfolio).toHaveBeenCalledWith(true, undefined, 'arquivos', 1, expect.any(AbortSignal));
});
test('exclui arquivo próprio e atualiza lista', async () => {
  lists([image]); api.deleteItem.mockImplementation(() => { lists(); return Promise.resolve(); }); render(<PortfolioSection owner />); await loaded();
  fireEvent.click(screen.getByRole('button', { name: 'Excluir obra.png' }));
  expect(await screen.findByText('Item excluído com sucesso.')).toBeInTheDocument(); await loaded();
  expect(api.deleteItem).toHaveBeenCalledWith('arquivos', 1); expect(screen.queryByText('obra.png')).not.toBeInTheDocument();
});
test('falha ao excluir mantém item e exibe erro', async () => {
  lists([image]); api.deleteItem.mockRejectedValue(new Error('Falha real de exclusão')); render(<PortfolioSection owner />); await loaded();
  fireEvent.click(screen.getByRole('button', { name: 'Excluir obra.png' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Falha real'); expect(screen.getByText('obra.png')).toBeInTheDocument();
});
test('vídeos usam iframe confiável e rejeitam HTML/arbitrário', async () => {
  lists([], [{ id: 1, provedor: 'YOUTUBE', embedUrl: 'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ' }, { id: 2, embedUrl: '<iframe src="javascript:evil()">' }]);
  const { container } = render(<PortfolioSection artistId={42} />); await loaded();
  expect(container.querySelectorAll('iframe')).toHaveLength(1); expect(screen.getByTitle('Vídeo do portfólio 1')).toHaveAttribute('loading', 'lazy');
  expect(screen.getByText('Vídeo indisponível.')).toBeInTheDocument(); expect(container.querySelector('script')).toBeNull();
});
test('cadastra link e permite excluir vídeo próprio', async () => {
  lists([], [{ id: 9, provedor: 'VIMEO', embedUrl: 'https://player.vimeo.com/video/123' }]); render(<PortfolioSection owner />); await loaded();
  fireEvent.change(screen.getByLabelText('Link YouTube ou Vimeo'), { target: { value: 'https://vimeo.com/123' } });
  fireEvent.click(screen.getByRole('button', { name: 'Adicionar vídeo' }));
  expect(await screen.findByText('Vídeo adicionado com sucesso.')).toBeInTheDocument(); await loaded();
  expect(api.addVideo).toHaveBeenCalledWith('https://vimeo.com/123');
  fireEvent.click(screen.getByRole('button', { name: 'Excluir vídeo 9' })); await loaded(); expect(api.deleteItem).toHaveBeenCalledWith('videos', 9);
});
test('erro de listagem permite tentar novamente', async () => {
  api.listPortfolio.mockRejectedValue(new Error('Portfólio indisponível')); render(<PortfolioSection owner />); await loaded();
  expect(screen.getByRole('alert')).toHaveTextContent('Portfólio indisponível'); lists(); fireEvent.click(screen.getByText('Tentar novamente'));
  expect(await screen.findByText('Nenhum arquivo no portfólio.')).toBeInTheDocument();
});
