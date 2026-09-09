import { act, fireEvent, render, screen } from '@testing-library/react';
import CandidaturaReviewPanel from './CandidaturaReviewPanel';
import { getSuggestedArtists } from '../../services/vagas/vagaService';
import { analyzeCandidatura } from '../../services/candidaturas/candidaturaService';
import ApiError from '../../services/api/ApiError';

jest.mock('../../services/vagas/vagaService', () => ({ getSuggestedArtists: jest.fn() }));
jest.mock('../../services/candidaturas/candidaturaService', () => ({ analyzeCandidatura: jest.fn() }));
const application = { candidaturaId: 7, artistaId: 9, nomeArtista: 'Pessoa sintética', status: 'PENDENTE', mensagemApresentacao: 'Minha experiência', linkPortfolioCandidatura: 'https://example.com/portfolio' };
beforeEach(() => {
  jest.resetAllMocks();
  getSuggestedArtists.mockResolvedValue({ content: [application], hasNext: false });
});

test('proprietário analisa, atualiza pelo retorno e não repete o envio pendente', async () => {
  let resolve;
  analyzeCandidatura.mockReturnValue(new Promise((done) => { resolve = done; }));
  render(<CandidaturaReviewPanel vacancyId="42" />);
  const button = await screen.findByRole('button', { name: 'Iniciar análise' });
  expect(screen.getByText('Minha experiência')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Ver portfólio ou currículo' })).toHaveAttribute('href', application.linkPortfolioCandidatura);
  fireEvent.click(button);
  fireEvent.click(button);
  expect(analyzeCandidatura).toHaveBeenCalledTimes(1);
  expect(analyzeCandidatura).toHaveBeenCalledWith('42', application, 'EM_ANALISE');
  await act(async () => resolve({ status: 'EM_ANALISE' }));
  expect(screen.getByText('Em análise')).toBeInTheDocument();
  expect(screen.getByText('Análise registrada com sucesso.')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Iniciar análise' })).not.toBeInTheDocument();
});

test('erro não falsifica sucesso nem estado final', async () => {
  analyzeCandidatura.mockRejectedValue(new ApiError({ status: 403, message: 'Somente o proprietário pode analisar.' }));
  render(<CandidaturaReviewPanel vacancyId="42" />);
  fireEvent.click(await screen.findByRole('button', { name: 'Aprovar' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Somente o proprietário pode analisar.');
  expect(screen.getByText('Pendente')).toBeInTheDocument();
  expect(screen.queryByText('Análise registrada com sucesso.')).not.toBeInTheDocument();
});

test.each(['APROVADO', 'REJEITADO', 'RETIRADA', 'CANCELADA_POR_VAGA'])('não analisa estado final %s', async (status) => {
  getSuggestedArtists.mockResolvedValue({ content: [{ ...application, status }], hasNext: false });
  render(<CandidaturaReviewPanel vacancyId="42" />);
  await screen.findByText(application.nomeArtista);
  expect(screen.queryByRole('button', { name: 'Aprovar' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Rejeitar' })).not.toBeInTheDocument();
});

test('paginação solicita somente vinte por página e preserva texto não confiável', async () => {
  getSuggestedArtists.mockResolvedValueOnce({ content: [{ ...application, linkPortfolioCandidatura: 'javascript:alert(1)', mensagemApresentacao: '<script>ataque</script>' }], hasNext: true });
  render(<CandidaturaReviewPanel vacancyId="42" />);
  await screen.findByText('<script>ataque</script>');
  expect(screen.queryByRole('link', { name: 'Ver portfólio ou currículo' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Próxima página' }));
  await screen.findByText('Página 2');
  expect(getSuggestedArtists).toHaveBeenLastCalledWith('42', { page: 1, size: 20 });
});
