import { useEffect, useRef, useState } from 'react';
import { useLocation, useParams } from 'react-router-dom';
import ContractorVacancyLayout from '../../components/vagas/ContractorVacancyLayout';
import VacancyCancelDialog from '../../components/vagas/VacancyCancelDialog';
import VacancyStatusBadge from '../../components/vagas/VacancyStatusBadge';
import CandidaturaReviewPanel from '../../components/candidaturas/CandidaturaReviewPanel';
import {
  cancelVacancy,
  changeVacancyStatus,
  getManagedVacancy,
  getRelatedVacancies,
} from '../../services/vagas/vacancyManagementService';
import { vacancyManagementError } from '../../services/vagas/vacancyManagementMessages';
import { VALID_MANAGED_VACANCY_ID } from './VacancyEditPage';

const STATUS_ACTIONS = {
  ABERTA: [
    { action: 'SUSPENDER', label: 'Suspender vaga' },
    { action: 'ENCERRAR', label: 'Encerrar vaga' },
  ],
  PAUSADA: [
    { action: 'REABRIR', label: 'Reabrir vaga' },
    { action: 'ENCERRAR', label: 'Encerrar vaga' },
  ],
  ENCERRADA: [],
  CANCELADA: [],
};

function money(value) {
  return Number(value || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

function RelatedVacancies({ vacancies }) {
  if (!vacancies.length) return null;
  return (
    <aside className="management-related" aria-labelledby="related-title">
      <h2 id="related-title">Outras vagas abertas</h2>
      {vacancies.map((vacancy) => <article key={vacancy.id}><h3>{vacancy.titulo}</h3><p>{vacancy.cidade}/{vacancy.estado} · {vacancy.modeloTrabalho}</p><a href={`/vagas/${vacancy.id}`}>Ver vaga</a></article>)}
    </aside>
  );
}

export default function VacancyManagePage() {
  const { id } = useParams();
  const location = useLocation();
  const [state, setState] = useState({ status: 'loading' });
  const [feedback, setFeedback] = useState(location.state?.created ? 'Vaga publicada com sucesso.' : location.state?.updated ? 'Vaga atualizada com sucesso.' : '');
  const [actionError, setActionError] = useState('');
  const [activeAction, setActiveAction] = useState('');
  const [cancelOpen, setCancelOpen] = useState(false);
  const actionRef = useRef(false);

  useEffect(() => {
    document.title = 'Gerenciar vaga — Palco';
    if (!VALID_MANAGED_VACANCY_ID.test(id || '')) {
      setState({ status: 'error', error: new Error('Identificador de vaga inválido.') });
      return undefined;
    }
    let active = true;
    Promise.all([
      getManagedVacancy(id),
      getRelatedVacancies(id).catch(() => ({ content: [] })),
    ]).then(([vacancy, related]) => {
      if (!active) return;
      if (!vacancy.propriaDoContratante) {
        setState({ status: 'forbidden' });
        return;
      }
      document.title = `Gerenciar ${vacancy.titulo} — Palco`;
      setState({ status: 'success', vacancy, related: (related.content || []).filter((item) => item.status === 'ABERTA').slice(0, 3) });
      if (window.location.hash === '#cancelar' && ['ABERTA', 'PAUSADA'].includes(vacancy.status)) setCancelOpen(true);
    }).catch((error) => {
      if (active) setState({ status: 'error', error });
    });
    return () => { active = false; };
  }, [id]);

  async function runStatusAction(action) {
    if (actionRef.current) return;
    actionRef.current = true;
    setActiveAction(action);
    setActionError('');
    setFeedback('');
    try {
      const updated = await changeVacancyStatus(id, action);
      setState((current) => ({ ...current, vacancy: updated }));
      setFeedback({ SUSPENDER: 'Vaga suspensa com sucesso.', REABRIR: 'Vaga reaberta com sucesso.', ENCERRAR: 'Vaga encerrada com sucesso.' }[action]);
    } catch (error) {
      setActionError(vacancyManagementError(error, 'Não foi possível alterar o status da vaga.'));
    } finally {
      actionRef.current = false;
      setActiveAction('');
    }
  }

  async function cancel(reason) {
    setActionError('');
    setFeedback('');
    try {
      await cancelVacancy(id, reason);
      setState((current) => ({ ...current, vacancy: { ...current.vacancy, status: 'CANCELADA' } }));
      setCancelOpen(false);
      setFeedback('Vaga cancelada. As candidaturas existentes foram preservadas no histórico pelo servidor.');
    } catch (error) {
      setActionError(vacancyManagementError(error, 'Não foi possível cancelar a vaga.'));
    }
  }

  let content;
  if (state.status === 'loading') content = <section className="management-state" role="status"><p>Carregando gestão da vaga…</p></section>;
  else if (state.status === 'forbidden') content = <section className="management-state management-state--error" role="alert"><h1>Acesso negado</h1><p>Esta vaga pertence a outro contratante.</p><a className="btn btn--primario" href="/minhas-vagas">Voltar às minhas vagas</a></section>;
  else if (state.status === 'error') content = <section className="management-state management-state--error" role="alert"><h1>Não foi possível abrir a gestão</h1><p>{vacancyManagementError(state.error, 'Tente novamente.')}</p><a className="btn btn--primario" href="/minhas-vagas">Voltar às minhas vagas</a></section>;
  else {
    const { vacancy, related } = state;
    const actions = STATUS_ACTIONS[vacancy.status] || [];
    const cancelable = vacancy.status === 'ABERTA' || vacancy.status === 'PAUSADA';
    content = <><header className="management-page-header management-page-header--actions"><div><p className="management-eyebrow">Detalhe do proprietário</p><h1>{vacancy.titulo}</h1><div className="management-title-status"><VacancyStatusBadge status={vacancy.status} /><span>{vacancy.categoria || vacancy.tipoContrato}</span></div></div><a className="btn management-button-secondary" href="/minhas-vagas">Voltar</a></header>{feedback ? <p className="management-feedback management-feedback--success" role="status">{feedback}</p> : null}{actionError ? <p className="management-feedback management-feedback--error" role="alert">{actionError}</p> : null}<div className="management-detail-layout"><article className="management-card management-detail"><div className="management-detail__actions"><a className="btn management-button-secondary" href={`/vagas/${id}/editar`}>Editar vaga</a>{actions.map((item) => <button className="btn btn--primario" key={item.action} type="button" disabled={Boolean(activeAction)} onClick={() => runStatusAction(item.action)}>{activeAction === item.action ? 'Processando…' : item.label}</button>)}{cancelable ? <button className="btn management-button-danger" id="cancelar" type="button" disabled={Boolean(activeAction)} onClick={() => setCancelOpen(true)}>Cancelar vaga</button> : null}</div>{actions.length === 0 ? <p className="management-final-state">Este é um estado final; nenhuma nova transição está disponível.</p> : null}<dl className="management-detail__facts"><div><dt>Contratante</dt><dd>{vacancy.nomeContratante}</dd></div><div><dt>Local</dt><dd>{vacancy.cidade}/{vacancy.estado}</dd></div><div><dt>Modelo</dt><dd>{vacancy.modeloTrabalho}</dd></div><div><dt>Contrato</dt><dd>{vacancy.tipoContrato}</dd></div><div><dt>Remuneração</dt><dd>{money(vacancy.remuneraValor)} · {vacancy.formaPagamento}</dd></div><div><dt>Prazo</dt><dd>{vacancy.dataLimiteCandidatura || 'Não informado'}</dd></div></dl><section><h2>Descrição</h2><p className="management-preserve-text">{vacancy.descricao}</p></section><section><h2>Requisitos</h2><p className="management-preserve-text">{vacancy.requisitos}</p></section>{vacancy.beneficios ? <section><h2>Benefícios</h2><p className="management-preserve-text">{vacancy.beneficios}</p></section> : null}{vacancy.tagIds?.length ? <section><h2>Tags</h2><div className="management-tag-list">{vacancy.tagIds.map((tagId) => <span key={tagId}>Área #{tagId}</span>)}</div></section> : null}</article><RelatedVacancies vacancies={related} /></div><VacancyCancelDialog open={cancelOpen} vacancyTitle={vacancy.titulo} onClose={() => setCancelOpen(false)} onConfirm={cancel} /></>;
  }

  return <ContractorVacancyLayout><main className="vacancy-management">{content}{state.status === 'success' ? <CandidaturaReviewPanel key={`${id}-${state.vacancy.status}`} vacancyId={id} /> : null}</main></ContractorVacancyLayout>;
}

export { STATUS_ACTIONS };
