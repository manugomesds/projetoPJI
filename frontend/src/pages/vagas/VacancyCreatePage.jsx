import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ContractorVacancyLayout from '../../components/vagas/ContractorVacancyLayout';
import VacancyForm from '../../components/vagas/VacancyForm';
import {
  createVacancy,
  listVacancyTags,
} from '../../services/vagas/vacancyManagementService';
import { vacancyManagementError } from '../../services/vagas/vacancyManagementMessages';

export default function VacancyCreatePage() {
  const navigate = useNavigate();
  const [tagsState, setTagsState] = useState({ status: 'loading', tags: [] });
  const [feedback, setFeedback] = useState('');

  useEffect(() => {
    document.title = 'Publicar nova vaga — Palco';
    let active = true;
    listVacancyTags()
      .then((tags) => {
        if (active) setTagsState({ status: 'success', tags: Array.isArray(tags) ? tags : [] });
      })
      .catch((error) => {
        if (active) setTagsState({ status: 'error', tags: [], error });
      });
    return () => { active = false; };
  }, []);

  async function publish(payload) {
    setFeedback('');
    try {
      const vacancy = await createVacancy(payload);
      navigate(`/vagas/${vacancy.id}/gerenciar`, { replace: true, state: { created: true } });
    } catch (error) {
      setFeedback(vacancyManagementError(error, 'Não foi possível publicar a vaga.'));
    }
  }

  return (
    <ContractorVacancyLayout>
      <main className="vacancy-management">
        <header className="management-page-header"><p className="management-eyebrow">RF04</p><h1>Publicar nova vaga</h1><p>Os dados de propriedade e o status inicial são definidos com segurança pelo servidor.</p></header>
        <section className="management-card">
          {tagsState.status === 'loading' ? <p role="status">Carregando funções…</p> : null}
          {tagsState.status === 'error' ? <p className="management-feedback management-feedback--error" role="alert">{vacancyManagementError(tagsState.error, 'Não foi possível carregar as funções.')}</p> : null}
          {feedback ? <p className="management-feedback management-feedback--error" role="alert">{feedback}</p> : null}
          {tagsState.status !== 'loading' ? <VacancyForm tags={tagsState.tags} submitLabel="Publicar vaga" onSubmit={publish} /> : null}
        </section>
      </main>
    </ContractorVacancyLayout>
  );
}
