import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import ApiError from '../../services/api/ApiError';
import { createCandidatura, withdrawCandidatura } from '../../services/candidaturas/candidaturaService';
import CandidaturaAction, { LINK_MAX_LENGTH, MESSAGE_MAX_LENGTH } from './CandidaturaAction';

jest.mock('../../services/candidaturas/candidaturaService', () => ({
  createCandidatura: jest.fn(),
  withdrawCandidatura: jest.fn(),
}));

const vaga = {
  id: 42,
  titulo: 'Guitarrista para festival',
  status: 'ABERTA',
  minhaCandidaturaId: null,
  statusMinhaCandidatura: null,
};

const artista = { token: 'jwt-artista', tipoUsuario: 'ARTISTA', perfilCompleto: true };
const contratante = { token: 'jwt-contratante', tipoUsuario: 'CONTRATANTE' };

function renderAction({ vagaAtual = vaga, session = artista } = {}) {
  return render(<CandidaturaAction vaga={vagaAtual} session={session} />);
}

function openAndFillForm() {
  fireEvent.click(screen.getByRole('button', { name: 'Candidatar-se' }));
  fireEvent.change(screen.getByLabelText('Mensagem de apresentação'), {
    target: { value: 'Tenho experiência e interesse nesta oportunidade.' },
  });
  fireEvent.change(screen.getByLabelText('Link do portfólio ou currículo'), {
    target: { value: 'https://portfolio.example/artista' },
  });
}

beforeEach(() => {
  createCandidatura.mockReset();
  withdrawCandidatura.mockReset();
});

test('visitante anônimo recebe link real para o login legado', () => {
  renderAction({ session: null });

  expect(screen.getByRole('link', { name: 'Entrar para candidatar-se' })).toHaveAttribute(
    'href',
    '/login'
  );
  expect(screen.queryByRole('button', { name: 'Candidatar-se' })).not.toBeInTheDocument();
});

test('contratante não recebe ação de candidatura', () => {
  renderAction({ session: contratante });

  expect(screen.getByText('Somente perfis de artista podem se candidatar a esta vaga.')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Candidatar-se' })).not.toBeInTheDocument();
});

test('artista recebe a ação mesmo se o storage indicar perfil incompleto', () => {
  renderAction({ session: { ...artista, perfilCompleto: false } });

  expect(screen.getByRole('button', { name: 'Candidatar-se' })).toBeInTheDocument();
});

test.each(['PAUSADA', 'ENCERRADA', 'CANCELADA'])(
  'vaga %s não permite nova candidatura',
  (status) => {
    renderAction({ vagaAtual: { ...vaga, status } });

    expect(screen.getByText('Esta vaga não aceita novas candidaturas.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Candidatar-se' })).not.toBeInTheDocument();
  }
);

test('reutiliza evidência de candidatura retornada no detalhe', () => {
  renderAction({
    vagaAtual: { ...vaga, minhaCandidaturaId: 91, statusMinhaCandidatura: 'EM_ANALISE' },
  });

  expect(screen.getByRole('heading', { name: 'Você já se candidatou' })).toBeInTheDocument();
  expect(screen.getByText('Em análise')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Candidatar-se' })).not.toBeInTheDocument();
});

test('envia o payload correto e mostra sucesso somente após 201', async () => {
  createCandidatura.mockResolvedValue({ id: 77, vagaId: 42, status: 'PENDENTE' });
  renderAction();
  openAndFillForm();

  fireEvent.click(screen.getByRole('button', { name: 'Enviar candidatura' }));

  await waitFor(() =>
    expect(createCandidatura).toHaveBeenCalledWith({
      vagaId: 42,
      mensagemApresentacao: 'Tenho experiência e interesse nesta oportunidade.',
      linkPortfolioCandidatura: 'https://portfolio.example/artista',
    })
  );
  expect(await screen.findByRole('heading', { name: 'Você já se candidatou' })).toBeInTheDocument();
  expect(screen.getByText('Pendente')).toBeInTheDocument();
});

test('mantém loading e bloqueia duplo submit enquanto o POST está pendente', async () => {
  let resolveRequest;
  createCandidatura.mockReturnValue(
    new Promise((resolve) => {
      resolveRequest = resolve;
    })
  );
  renderAction();
  openAndFillForm();
  const form = screen.getByRole('button', { name: 'Enviar candidatura' }).closest('form');

  fireEvent.submit(form);
  fireEvent.submit(form);

  expect(createCandidatura).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('button', { name: 'Enviando…' })).toBeDisabled();

  await act(async () => resolveRequest({ id: 77, status: 'PENDENTE' }));
  expect(await screen.findByRole('heading', { name: 'Você já se candidatou' })).toBeInTheDocument();
});

test.each([
  [403, 'Somente artistas podem se candidatar.'],
  [404, 'Vaga não encontrada.'],
  [409, 'Já existe candidatura para esta vaga e artista.'],
  [422, 'A vaga não aceita candidaturas porque está com status PAUSADA.'],
])('preserva erro HTTP %i no formulário', async (status, message) => {
  createCandidatura.mockRejectedValue(new ApiError({ status, message }));
  renderAction();
  openAndFillForm();

  fireEvent.click(screen.getByRole('button', { name: 'Enviar candidatura' }));

  expect(await screen.findByRole('alert')).toHaveTextContent(message);
  expect(screen.getByRole('button', { name: 'Enviar candidatura' })).toBeEnabled();
});

test('422 de perfil incompleto oferece o destino privado legado', async () => {
  createCandidatura.mockRejectedValue(
    new ApiError({ status: 422, message: 'Complete seu perfil antes de se candidatar.' })
  );
  renderAction();
  openAndFillForm();

  fireEvent.click(screen.getByRole('button', { name: 'Enviar candidatura' }));

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Complete seu perfil antes de se candidatar.'
  );
  expect(screen.getByRole('link', { name: 'Completar meu perfil' })).toHaveAttribute(
    'href',
    '/perfil'
  );
});

test('aplica os limites 2000 e 255 no formulário e exibe contadores', () => {
  renderAction();
  fireEvent.click(screen.getByRole('button', { name: 'Candidatar-se' }));

  expect(screen.getByLabelText('Mensagem de apresentação')).toHaveAttribute(
    'maxLength',
    String(MESSAGE_MAX_LENGTH)
  );
  expect(screen.getByLabelText('Link do portfólio ou currículo')).toHaveAttribute(
    'maxLength',
    String(LINK_MAX_LENGTH)
  );
  expect(screen.getByText('0/2000')).toBeInTheDocument();
  expect(screen.getByText('0/255')).toBeInTheDocument();
});

test('mensagem de erro não confiável é renderizada somente como texto', async () => {
  const untrusted = '<script>window.comprometido = true</script>';
  createCandidatura.mockRejectedValue(new ApiError({ status: 409, message: untrusted }));
  const { container } = renderAction();
  openAndFillForm();

  fireEvent.click(screen.getByRole('button', { name: 'Enviar candidatura' }));

  expect(await screen.findByText(untrusted)).toBeInTheDocument();
  expect(container.querySelector('script')).not.toBeInTheDocument();
  expect(window.comprometido).toBeUndefined();
});

test.each(['PENDENTE', 'EM_ANALISE', 'REJEITADO'])('retira candidatura %s e mantém histórico', async (status) => {
  withdrawCandidatura.mockResolvedValue(null);
  renderAction({ vagaAtual: { ...vaga, minhaCandidaturaId: 91, statusMinhaCandidatura: status } });
  fireEvent.click(screen.getByRole('button', { name: 'Retirar candidatura' }));
  expect(await screen.findByText('Candidatura retirada. Seu histórico foi preservado.')).toBeInTheDocument();
  expect(withdrawCandidatura).toHaveBeenCalledWith(91);
  expect(screen.getByText('Retirada')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Candidatar-se' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Retirar candidatura' })).not.toBeInTheDocument();
});

test.each(['APROVADO', 'RETIRADA', 'CANCELADA_POR_VAGA'])('estado %s não oferece retirada', (status) => {
  renderAction({ vagaAtual: { ...vaga, minhaCandidaturaId: 91, statusMinhaCandidatura: status } });
  expect(screen.queryByRole('button', { name: 'Retirar candidatura' })).not.toBeInTheDocument();
});

test('erro na retirada preserva status e permite tentar novamente', async () => {
  withdrawCandidatura.mockRejectedValue(new ApiError({ status: 422, message: 'Transição inválida.' }));
  renderAction({ vagaAtual: { ...vaga, minhaCandidaturaId: 91, statusMinhaCandidatura: 'PENDENTE' } });
  fireEvent.click(screen.getByRole('button', { name: 'Retirar candidatura' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Transição inválida.');
  expect(screen.getByText('Pendente')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Retirar candidatura' })).toBeEnabled();
});
