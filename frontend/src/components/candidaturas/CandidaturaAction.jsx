import { useRef, useState } from 'react';
import ApiError from '../../services/api/ApiError';
import { createCandidatura, withdrawCandidatura } from '../../services/candidaturas/candidaturaService';

export const MESSAGE_MAX_LENGTH = 2000;
export const LINK_MAX_LENGTH = 255;

export const STATUS_LABELS = {
  PENDENTE: 'Pendente',
  EM_ANALISE: 'Em análise',
  APROVADO: 'Aprovada',
  REJEITADO: 'Rejeitada',
  RETIRADA: 'Retirada',
  CANCELADA_POR_VAGA: 'Cancelada com a vaga',
};

function applicationError(error) {
  if (!(error instanceof ApiError)) {
    return { message: 'Não foi possível enviar sua candidatura. Tente novamente.' };
  }

  const message = error.message || `Falha na candidatura (HTTP ${error.status}).`;
  return {
    message,
    profileRequired: error.status === 422 && /perfil/i.test(message),
  };
}

function ExistingApplication({ id, status, session }) {
  const [withdrawn, setWithdrawn] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const pending = useRef(false);
  const currentStatus = withdrawn ? 'RETIRADA' : status;
  const eligible = id && session?.token && session.tipoUsuario === 'ARTISTA'
    && ['PENDENTE', 'EM_ANALISE', 'REJEITADO'].includes(currentStatus);

  async function withdraw() {
    if (pending.current) return;
    pending.current = true;
    setLoading(true);
    setError('');
    try {
      await withdrawCandidatura(id);
      setWithdrawn(true);
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : 'Não foi possível retirar sua candidatura. Tente novamente.');
    } finally {
      pending.current = false;
      setLoading(false);
    }
  }
  return (
    <section className="candidatura candidatura--sucesso" aria-labelledby="candidatura-titulo">
      <p className="dashboard__sobrelinha">Candidatura registrada</p>
      <h2 id="candidatura-titulo">Você já se candidatou</h2>
      <p>
        Status atual: <strong>{STATUS_LABELS[currentStatus] || currentStatus || 'Registrada'}</strong>
      </p>
      {withdrawn ? <p role="status">Candidatura retirada. Seu histórico foi preservado.</p> : null}
      {error ? <p role="alert">{error}</p> : null}
      {eligible ? <button className="btn-dash btn-dash--secundario" type="button" disabled={loading} onClick={withdraw}>{loading ? 'Retirando…' : 'Retirar candidatura'}</button> : null}
    </section>
  );
}

export default function CandidaturaAction({ vaga, session }) {
  const [formOpen, setFormOpen] = useState(false);
  const [fields, setFields] = useState({
    mensagemApresentacao: '',
    linkPortfolioCandidatura: '',
  });
  const [submission, setSubmission] = useState({ status: 'idle' });
  const submittingRef = useRef(false);

  const candidatura = submission.status === 'success' ? submission.candidatura : null;
  const hasExistingApplication =
    Boolean(candidatura) ||
    (vaga.minhaCandidaturaId !== null && vaga.minhaCandidaturaId !== undefined) ||
    Boolean(vaga.statusMinhaCandidatura);
  const existingStatus = candidatura?.status || vaga.statusMinhaCandidatura;

  if (hasExistingApplication) {
    return <ExistingApplication id={candidatura?.id || vaga.minhaCandidaturaId} status={existingStatus} session={session} />;
  }

  if (vaga.status !== 'ABERTA') {
    return (
      <section className="candidatura candidatura--indisponivel" aria-label="Candidatura indisponível">
        <h2>Candidaturas encerradas</h2>
        <p>Esta vaga não aceita novas candidaturas.</p>
      </section>
    );
  }

  if (!session?.token) {
    return (
      <section className="candidatura" aria-labelledby="candidatura-login-titulo">
        <h2 id="candidatura-login-titulo">Quer participar desta oportunidade?</h2>
        <p>Entre como artista para enviar sua candidatura.</p>
        <a className="btn-dash btn-dash--primario" href="/login">
          Entrar para candidatar-se
        </a>
      </section>
    );
  }

  if (session.tipoUsuario !== 'ARTISTA') {
    return (
      <section className="candidatura candidatura--indisponivel" aria-label="Ação exclusiva para artistas">
        <h2>Candidatura para artistas</h2>
        <p>Somente perfis de artista podem se candidatar a esta vaga.</p>
      </section>
    );
  }

  function updateField(event) {
    const { name, value } = event.target;
    setFields((current) => ({ ...current, [name]: value }));
    if (submission.status === 'error') setSubmission({ status: 'idle' });
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (submittingRef.current || submission.status === 'success') return;

    if (fields.mensagemApresentacao.length > MESSAGE_MAX_LENGTH) {
      setSubmission({ status: 'error', message: 'A apresentação deve ter no máximo 2000 caracteres.' });
      return;
    }
    if (fields.linkPortfolioCandidatura.length > LINK_MAX_LENGTH) {
      setSubmission({ status: 'error', message: 'O link deve ter no máximo 255 caracteres.' });
      return;
    }

    submittingRef.current = true;
    setSubmission({ status: 'loading' });

    try {
      const response = await createCandidatura({ vagaId: vaga.id, ...fields });
      setSubmission({ status: 'success', candidatura: response });
    } catch (error) {
      setSubmission({ status: 'error', ...applicationError(error) });
    } finally {
      submittingRef.current = false;
    }
  }

  if (!formOpen) {
    return (
      <section className="candidatura" aria-labelledby="candidatura-acao-titulo">
        <p className="dashboard__sobrelinha">RF06 · Candidatura</p>
        <h2 id="candidatura-acao-titulo">Candidate-se a esta vaga</h2>
        <p>Apresente seu trabalho e compartilhe seu portfólio ou currículo.</p>
        <button
          className="btn-dash btn-dash--primario"
          type="button"
          onClick={() => setFormOpen(true)}
        >
          Candidatar-se
        </button>
      </section>
    );
  }

  return (
    <section className="candidatura" aria-labelledby="candidatura-form-titulo">
      <p className="dashboard__sobrelinha">RF06 · Candidatura</p>
      <h2 id="candidatura-form-titulo">Sua apresentação</h2>
      <form className="candidatura__form" onSubmit={handleSubmit}>
        <label htmlFor="mensagem-apresentacao">Mensagem de apresentação</label>
        <textarea
          id="mensagem-apresentacao"
          name="mensagemApresentacao"
          value={fields.mensagemApresentacao}
          onChange={updateField}
          maxLength={MESSAGE_MAX_LENGTH}
          rows="7"
          required
          disabled={submission.status === 'loading'}
        />
        <span className="candidatura__contador" aria-live="polite">
          {fields.mensagemApresentacao.length}/{MESSAGE_MAX_LENGTH}
        </span>

        <label htmlFor="link-portfolio">Link do portfólio ou currículo</label>
        <input
          id="link-portfolio"
          name="linkPortfolioCandidatura"
          type="url"
          value={fields.linkPortfolioCandidatura}
          onChange={updateField}
          maxLength={LINK_MAX_LENGTH}
          required
          disabled={submission.status === 'loading'}
          placeholder="https://"
        />
        <span className="candidatura__contador" aria-live="polite">
          {fields.linkPortfolioCandidatura.length}/{LINK_MAX_LENGTH}
        </span>

        {submission.status === 'error' && (
          <div className="candidatura__erro" role="alert">
            <p>{submission.message}</p>
            {submission.profileRequired ? (
              <a href="/perfil">Completar meu perfil</a>
            ) : null}
          </div>
        )}

        <div className="candidatura__acoes">
          <button
            className="btn-dash btn-dash--primario"
            type="submit"
            disabled={submission.status === 'loading'}
          >
            {submission.status === 'loading' ? 'Enviando…' : 'Enviar candidatura'}
          </button>
          <button
            className="btn-dash btn-dash--secundario"
            type="button"
            onClick={() => setFormOpen(false)}
            disabled={submission.status === 'loading'}
          >
            Cancelar
          </button>
        </div>
      </form>
    </section>
  );
}

export { applicationError };
