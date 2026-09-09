import { act, render, screen } from '@testing-library/react';
import apiClient from '../../services/api/apiClient';
import VagaRecommendations from './VagaRecommendations';

jest.mock('../../services/api/apiClient', () => ({
  __esModule: true,
  default: { get: jest.fn() },
}));

function deferred() {
  let resolve;
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

beforeEach(() => {
  apiClient.get.mockReset();
  window.localStorage.clear();
  window.sessionStorage.clear();
});

test('expõe loading próprio sem bloquear o conteúdo principal', () => {
  apiClient.get.mockReturnValue(new Promise(() => {}));

  render(<VagaRecommendations vaga={{ id: 42, propriaDoContratante: true }} />);

  expect(screen.getByRole('status')).toHaveTextContent('Carregando artistas sugeridos…');
});

test('resposta antiga não sobrescreve recomendações após mudança da vaga', async () => {
  const oldRequest = deferred();
  apiClient.get.mockImplementation((path) => (
    path.startsWith('/vagas/42/')
      ? oldRequest.promise
      : Promise.resolve({
          content: [{
            candidaturaId: 2,
            artistaId: 22,
            nomeArtista: 'Artista da vaga atual',
            tagsCoincidentes: [],
            quantidadeTagsCoincidentes: 0,
          }],
        })
  ));
  const { rerender } = render(
    <VagaRecommendations vaga={{ id: 42, propriaDoContratante: true }} />
  );

  rerender(<VagaRecommendations vaga={{ id: 43, propriaDoContratante: true }} />);
  expect(await screen.findByText('Artista da vaga atual')).toBeInTheDocument();

  await act(async () => {
    oldRequest.resolve({
      content: [{
        candidaturaId: 1,
        artistaId: 11,
        nomeArtista: 'Resposta antiga',
        tagsCoincidentes: [],
        quantidadeTagsCoincidentes: 0,
      }],
    });
  });

  expect(screen.getByText('Artista da vaga atual')).toBeInTheDocument();
  expect(screen.queryByText('Resposta antiga')).not.toBeInTheDocument();
});
