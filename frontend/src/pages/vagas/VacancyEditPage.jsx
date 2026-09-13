import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import ContractorVacancyLayout from '../../components/vagas/ContractorVacancyLayout';
import VacancyForm from '../../components/vagas/VacancyForm';
import {
  getManagedVacancy,
  listVacancyTags,
  updateVacancy,
} from '../../services/vagas/vacancyManagementService';
import { vacancyManagementError } from '../../services/vagas/vacancyManagementMessages';

const VALID_ID = /^[1-9]\d*$/;

export default function VacancyEditPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [state, setState] = useState({ status: 'loading' });
  const [feedback, setFeedback] = useState('');

  useEffect(() => {
    document.title = 'Editar vaga — Palco';
    if (!VALID_ID.test(id || '')) {
      setState({ status: 'error', error: new Error('Identificador de vaga inválido.') });
      return undefined;
    }
    let active = true;
    Promise.all([getManagedVacancy(id), listVacancyTags()])
      .then(([vacancy, tags]) => {
        if (!active) return;
        if (!vacancy.propriaDoContratante) {
          setState({ status: 'forbidden' });
          return;
        }
        document.title = `Editar ${vacancy.titulo} — Palco`;
        setState({ status: 'success', vacancy, tags: Array.isArray(tags) ? tags : [] });
      })
      .catch((error) => {
        if (active) setState({ status: 'error', error });
      });
    return () => { active = false; };
  }, [id]);

  async function save(payload) {
    setFeedback('');
    try {
      await updateVacancy(id, payload);
      navigate(`/vagas/${id}/gerenciar`, { replace: true, state: { updated: true } });
    } catch (error) {
      setFeedback(vacancyManagementError(error, 'Não foi possível atualizar a vaga.'));
    }
  }

  let content;
  if (state.status === 'loading') content = <section className="management-state" role="status"><p>Carregando vaga e funções…</p></section>;
  else if (state.status === 'forbidden') content = <section className="management-state management-state--error" role="alert"><h1>Acesso negado</h1><p>Somente o proprietário pode editar esta vaga.</p><a className="btn btn--primario" href="/minhas-vagas">Voltar às minhas vagas</a></section>;
  else if (state.status === 'error') content = <section className="management-state management-state--error" role="alert"><h1>Não foi possível editar a vaga</h1><p>{vacancyManagementError(state.error, 'Tente novamente.')}</p><a className="btn btn--primario" href="/minhas-vagas">Voltar às minhas vagas</a></section>;
  else content = <><header className="management-page-header"><p className="management-eyebrow">RF07</p><h1>Editar vaga</h1><p>Propriedade, status, ID e data de publicação não fazem parte deste formulário.</p></header><section className="management-card">{feedback ? <p className="management-feedback management-feedback--error" role="alert">{feedback}</p> : null}<VacancyForm initialValue={state.vacancy} tags={state.tags} submitLabel="Salvar alterações" onSubmit={save} cancelHref={`/vagas/${id}/gerenciar`} /></section></>;

  return <ContractorVacancyLayout><main className="vacancy-management">{content}</main></ContractorVacancyLayout>;
}

export { VALID_ID as VALID_MANAGED_VACANCY_ID };
