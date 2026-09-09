import { useEffect, useState } from 'react';
import sessionService from '../../auth/sessionService';
import { getSimilarVacancies } from '../../services/vagas/vacancyListingService';
import { getSuggestedArtists } from '../../services/vagas/vagaService';
import VacancyCard from './VacancyCard';

function RecommendationsState({ kind, children }) {
  return (
    <div
      className={`rf05-recommendations__state rf05-recommendations__state--${kind}`}
      role={kind === 'loading' ? 'status' : kind === 'error' ? 'alert' : undefined}
    >
      {children}
    </div>
  );
}

function SuggestedArtistCard({ artist }) {
  const tagIds = Array.isArray(artist.tagsCoincidentes)
    ? artist.tagsCoincidentes
    : Array.from(artist.tagsCoincidentes || []);
  const matchCount = Number.isFinite(Number(artist.quantidadeTagsCoincidentes))
    ? Number(artist.quantidadeTagsCoincidentes)
    : tagIds.length;

  return (
    <article className="rf05-artist-card">
      <div className="rf05-artist-card__identity">
        {artist.avatarUrl ? (
          <img src={artist.avatarUrl} alt="" className="rf05-artist-card__avatar" />
        ) : (
          <span className="rf05-artist-card__avatar rf05-artist-card__avatar--fallback" aria-hidden="true">
            {(artist.nomeArtista || 'A').trim().charAt(0).toUpperCase()}
          </span>
        )}
        <div>
          <h3>{artist.nomeArtista || 'Artista'}</h3>
          {artist.localizacao ? <p>{artist.localizacao}</p> : null}
        </div>
      </div>
      <p className="rf05-artist-card__match">
        {matchCount === 1 ? '1 área compatível' : `${matchCount} áreas compatíveis`}
      </p>
      {artist.biografia ? <p className="rf05-artist-card__bio">{artist.biografia}</p> : null}
      {tagIds.length ? (
        <div className="rf05-artist-card__tags" aria-label="Áreas compatíveis">
          {tagIds.map((tagId) => <span key={tagId}>Área #{tagId}</span>)}
        </div>
      ) : null}
      {artist.artistaId ? (
        <a href={`/perfis/ARTISTA/${encodeURIComponent(artist.artistaId)}`}>
          Ver perfil público
        </a>
      ) : null}
    </article>
  );
}

function OwnerRecommendations({ state }) {
  return (
    <aside className="rf05-recommendations" aria-labelledby="rf05-artists-title">
      <header>
        <p className="dashboard__sobrelinha">Compatibilidade da vaga</p>
        <h2 id="rf05-artists-title">Artistas sugeridos</h2>
        <p>Candidatos ordenados pelas áreas em comum e pela atualização do perfil.</p>
      </header>
      {state.status === 'loading' ? (
        <RecommendationsState kind="loading">Carregando artistas sugeridos…</RecommendationsState>
      ) : null}
      {state.status === 'error' ? (
        <RecommendationsState kind="error">
          Não foi possível carregar os artistas sugeridos. O detalhe da vaga continua disponível.
        </RecommendationsState>
      ) : null}
      {state.status === 'success' && state.items.length === 0 ? (
        <RecommendationsState kind="empty">
          Ainda não há artistas candidatos para sugerir nesta vaga.
        </RecommendationsState>
      ) : null}
      {state.status === 'success' && state.items.length > 0 ? (
        <div className="rf05-recommendations__list">
          {state.items.map((artist) => (
            <SuggestedArtistCard
              key={artist.candidaturaId || artist.artistaId}
              artist={artist}
            />
          ))}
        </div>
      ) : null}
    </aside>
  );
}

function ArtistRecommendations({ state }) {
  return (
    <aside className="rf05-recommendations" aria-labelledby="rf05-similar-title">
      <header>
        <p className="dashboard__sobrelinha">Mais oportunidades</p>
        <h2 id="rf05-similar-title">Vagas similares</h2>
      </header>
      {state.status === 'loading' ? (
        <RecommendationsState kind="loading">Carregando vagas similares…</RecommendationsState>
      ) : null}
      {state.status === 'error' ? (
        <RecommendationsState kind="error">
          Não foi possível carregar as vagas similares. O detalhe da vaga continua disponível.
        </RecommendationsState>
      ) : null}
      {state.status === 'success' && state.items.length === 0 ? (
        <RecommendationsState kind="empty">Nenhuma vaga similar aberta foi encontrada.</RecommendationsState>
      ) : null}
      {state.status === 'success' && state.items.length > 0 ? (
        <div className="rf05-recommendations__list rf05-recommendations__list--vacancies">
          {state.items.map((vacancy, index) => (
            <VacancyCard key={vacancy.id} vacancy={vacancy} index={index} />
          ))}
        </div>
      ) : null}
    </aside>
  );
}

export default function VagaRecommendations({ vaga }) {
  const session = sessionService.getSession();
  const mode = vaga.propriaDoContratante === true
    ? 'owner'
    : session?.token && session.tipoUsuario === 'ARTISTA'
      ? 'artist'
      : null;
  const vacancyId = vaga.id;
  const [state, setState] = useState({ status: mode ? 'loading' : 'idle', items: [] });

  useEffect(() => {
    if (!mode || !vacancyId) {
      setState({ status: 'idle', items: [] });
      return undefined;
    }

    let active = true;
    setState({ status: 'loading', items: [] });
    const request = mode === 'owner'
      ? getSuggestedArtists(vacancyId, { page: 0, size: 3 })
      : getSimilarVacancies(vacancyId, { size: 3 });

    request
      .then((response) => {
        if (!active) return;
        const items = Array.isArray(response?.content) ? response.content : [];
        const visibleItems = mode === 'artist'
          ? items.filter((item) => item.status === 'ABERTA' && String(item.id) !== String(vacancyId)).slice(0, 3)
          : items.slice(0, 3);
        setState({ status: 'success', items: visibleItems });
      })
      .catch(() => {
        if (active) setState({ status: 'error', items: [] });
      });

    return () => {
      active = false;
    };
  }, [mode, vacancyId]);

  if (mode === 'owner') return <OwnerRecommendations state={state} />;
  if (mode === 'artist') return <ArtistRecommendations state={state} />;
  return null;
}
