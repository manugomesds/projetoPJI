import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import sessionService from '../../auth/sessionService';
import CandidaturaAction from '../../components/candidaturas/CandidaturaAction';
import ErrorState from '../../components/common/ErrorState';
import LoadingState from '../../components/common/LoadingState';
import NotFound from '../../components/common/NotFound';
import VagaDetails from '../../components/vagas/VagaDetails';
import VagaRecommendations from '../../components/vagas/VagaRecommendations';
import ApiError from '../../services/api/ApiError';
import apiClient from '../../services/api/apiClient';
import { getVagaDetails } from '../../services/vagas/vagaService';

const VALID_ID = /^[1-9]\d*$/;

function OwnerActions({ vaga }) {
  const editable = vaga.status === 'ABERTA' || vaga.status === 'PAUSADA';
  const encodedId = encodeURIComponent(vaga.id);

  return (
    <section className="rf05-owner-actions" aria-labelledby="rf05-owner-actions-title">
      <div>
        <p className="dashboard__sobrelinha">Ações da sua vaga</p>
        <h2 id="rf05-owner-actions-title">Gerenciar oportunidade</h2>
      </div>
      <div className="rf05-owner-actions__links">
        <a className="btn-dash btn-dash--primario" href={`/vagas/${encodedId}/gerenciar`}>
          Gerenciar vaga
        </a>
        {editable ? (
          <>
            <a className="btn-dash btn-dash--secundario" href={`/vagas/${encodedId}/editar`}>
              Editar vaga
            </a>
            <a className="btn-dash btn-dash--perigo" href={`/vagas/${encodedId}/gerenciar#cancelar`}>
              Cancelar vaga
            </a>
          </>
        ) : null}
      </div>
    </section>
  );
}

function VagaPageHeader() {
  async function handleLogout() {
    const session = sessionService.getSession();

    try {
      if (session?.refreshToken) {
        await apiClient.post('/auth/logout', { refreshToken: session.refreshToken });
      }
    } catch (error) {
      console.warn('Não foi possível invalidar o refresh token.', error);
    }

    sessionService.clearLocalSession();
    window.location.assign('/login');
  }

  return (
    <header>
      <nav className="app-navbar" aria-label="Navegação principal">
        <a href="/dashboard">
          <img className="navbar__logo" src="/assets/logo-palco.png" alt="Palco" />
        </a>
        <div className="app-navbar__acoes">
          <a className="btn-dash btn-dash--secundario" href="/dashboard">
            Voltar ao painel
          </a>
          <button className="dashboard__sair" type="button" onClick={handleLogout}>
            Sair
          </button>
        </div>
      </nav>
    </header>
  );
}

export default function VagaDetailPage() {
  const { id } = useParams();
  const validId = typeof id === 'string' && VALID_ID.test(id);
  const [state, setState] = useState({ status: 'loading' });

  useEffect(() => {
    document.title = 'Detalhes da vaga — Palco';

    if (!validId) return undefined;

    let active = true;
    setState({ status: 'loading' });

    getVagaDetails(id)
      .then((vaga) => {
        if (!active) return;
        document.title = `${vaga.titulo} — Palco`;
        setState({ status: 'success', vaga });
      })
      .catch((error) => {
        if (!active) return;
        setState({
          status: error instanceof ApiError && error.status === 404 ? 'not-found' : 'error',
          error,
        });
      });

    return () => {
      active = false;
    };
  }, [id, validId]);

  let content;

  if (!validId) {
    content = (
      <ErrorState
        title="Não foi possível abrir esta vaga"
        error={new Error('Identificador de vaga inválido.')}
        className="dashboard-estado dashboard-estado--erro"
        headingLevel="h1"
      >
        <a className="btn-dash btn-dash--primario" href="/vagas">
          Voltar às vagas
        </a>
      </ErrorState>
    );
  } else if (state.status === 'loading') {
    content = (
      <LoadingState
        message="Carregando vaga…"
        className="dashboard-estado"
        indicatorClassName="dashboard-spinner"
      />
    );
  } else if (state.status === 'not-found') {
    content = (
      <NotFound
        embedded
        title="Não foi possível abrir esta vaga"
        message={state.error.message}
        backHref="/dashboard"
        backLabel="Voltar ao painel"
      />
    );
  } else if (state.status === 'error') {
    content = (
      <ErrorState
        title="Não foi possível abrir esta vaga"
        error={state.error}
        className="dashboard-estado dashboard-estado--erro"
        headingLevel="h1"
      >
        <a className="btn-dash btn-dash--primario" href="/vagas">
          Voltar às vagas
        </a>
      </ErrorState>
    );
  } else {
    const isOwner = state.vaga.propriaDoContratante === true;
    content = (
      <div className="rf05-detail-layout">
        <div className="rf05-detail-layout__main">
          <VagaDetails vaga={state.vaga} />
          {isOwner ? (
            <OwnerActions vaga={state.vaga} />
          ) : (
            <CandidaturaAction vaga={state.vaga} session={sessionService.getSession()} />
          )}
        </div>
        <VagaRecommendations vaga={state.vaga} />
      </div>
    );
  }

  return (
    <div className="pagina-app">
      <VagaPageHeader />
      <main className="dashboard">{content}</main>
    </div>
  );
}

export { VALID_ID };
