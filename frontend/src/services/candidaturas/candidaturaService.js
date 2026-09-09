import apiClient from '../api/apiClient';

export function createCandidatura({ vagaId, mensagemApresentacao, linkPortfolioCandidatura }) {
  return apiClient.post('/candidaturas', {
    vagaId: Number(vagaId),
    mensagemApresentacao,
    linkPortfolioCandidatura,
  });
}

export function withdrawCandidatura(id) {
  return apiClient.delete(`/candidaturas/${encodeURIComponent(id)}`);
}

export function analyzeCandidatura(vagaId, candidatura, status) {
  return apiClient.put(`/candidaturas/${encodeURIComponent(candidatura.candidaturaId)}`, {
    vagaId: Number(vagaId),
    artistaId: candidatura.artistaId,
    mensagemApresentacao: candidatura.mensagemApresentacao,
    linkPortfolioCandidatura: candidatura.linkPortfolioCandidatura,
    status,
  });
}
