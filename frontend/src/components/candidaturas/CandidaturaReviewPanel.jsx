import { useEffect, useRef, useState } from 'react';
import ApiError from '../../services/api/ApiError';
import { analyzeCandidatura } from '../../services/candidaturas/candidaturaService';
import { getSuggestedArtists } from '../../services/vagas/vagaService';
import { STATUS_LABELS } from './CandidaturaAction';

const ACTIONS = {
  PENDENTE: ['EM_ANALISE', 'APROVADO', 'REJEITADO'],
  EM_ANALISE: ['APROVADO', 'REJEITADO'],
};
const ACTION_LABELS = { EM_ANALISE: 'Iniciar análise', APROVADO: 'Aprovar', REJEITADO: 'Rejeitar' };

function ApplicationReview({ vacancyId, application }) {
  const [status, setStatus] = useState(application.status);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [feedback, setFeedback] = useState('');
  const pending = useRef(false);

  async function review(nextStatus) {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError('');
    setFeedback('');
    try {
      const updated = await analyzeCandidatura(vacancyId, application, nextStatus);
      setStatus(updated.status);
      setFeedback('Análise registrada com sucesso.');
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : 'Não foi possível registrar a análise. Tente novamente.');
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  // O contrato aceita texto livre; somente URLs HTTP(S) tornam-se links clicáveis.
  const portfolio = application.linkPortfolioCandidatura;
  let safePortfolio = false;
  try { safePortfolio = ['https:', 'http:'].includes(new URL(portfolio).protocol); } catch { /* Texto preservado. */ }

  return <article className="management-card" aria-label={`Candidatura de ${application.nomeArtista}`}>
    <h3>{application.nomeArtista}</h3>
    <p>Status: <strong>{STATUS_LABELS[status] || status}</strong></p>
    <p className="management-preserve-text">{application.mensagemApresentacao}</p>
    <p>{safePortfolio ? <a href={portfolio} target="_blank" rel="noopener noreferrer">Ver portfólio ou currículo</a> : portfolio}</p>
    <div className="management-detail__actions">
      {(ACTIONS[status] || []).map((next) => <button key={next} type="button" className="btn management-button-secondary" disabled={busy} onClick={() => review(next)}>{ACTION_LABELS[next]}</button>)}
    </div>
    {busy ? <p role="status">Registrando análise…</p> : null}
    {feedback ? <p role="status">{feedback}</p> : null}
    {error ? <p role="alert">{error}</p> : null}
  </article>;
}

export default function CandidaturaReviewPanel({ vacancyId }) {
  const [page, setPage] = useState(0);
  const [state, setState] = useState({ loading: true });
  useEffect(() => {
    let active = true;
    setState({ loading: true });
    getSuggestedArtists(vacancyId, { page, size: 20 }).then((data) => {
      if (active) setState({ data, loading: false });
    }).catch(() => {
      if (active) setState({ loading: false, error: 'Não foi possível carregar as candidaturas.' });
    });
    return () => { active = false; };
  }, [vacancyId, page]);

  return <section className="candidatura-review" aria-labelledby="received-applications-title">
    <h2 id="received-applications-title">Candidaturas recebidas</h2>
    {state.loading ? <p role="status">Carregando candidaturas…</p> : null}
    {state.error ? <p role="alert">{state.error}</p> : null}
    {state.data ? <>
      {!state.data.content.length ? <p>Nenhuma candidatura recebida.</p> : null}
      {state.data.content.map((application) => <ApplicationReview key={application.candidaturaId} vacancyId={vacancyId} application={application} />)}
      <div className="management-detail__actions">
        <button className="btn management-button-secondary" type="button" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>Página anterior</button>
        <span>Página {page + 1}</span>
        <button className="btn management-button-secondary" type="button" disabled={!state.data.hasNext} onClick={() => setPage((current) => current + 1)}>Próxima página</button>
      </div>
    </> : null}
  </section>;
}
