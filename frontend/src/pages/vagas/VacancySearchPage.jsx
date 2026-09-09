import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import BackToTopButton from '../../components/common/BackToTopButton';
import VacancyCard from '../../components/vagas/VacancyCard';
import {
  buildVacancyParams,
  getSimilarVacancies,
  getVacancyPage,
  VACANCY_FILTER_FIELDS,
} from '../../services/vagas/vacancyListingService';

const PAGE_SIZE = 20;
const EMPTY_FEED = {
  open: [],
  cancelled: [],
  cursor: null,
  cursorCanceladas: null,
  hasMore: true,
  hasMoreCanceladas: true,
  loading: false,
  error: '',
};

const EMPTY_SIMILARS = { status: 'idle', referenceId: null, items: [] };

function filtersFromParams(searchParams) {
  const filters = {};
  VACANCY_FILTER_FIELDS.forEach((field) => {
    const value = searchParams.get(field);
    if (value) filters[field] = field === 'estado' ? value.toUpperCase() : value;
  });
  return filters;
}

function uniqueById(current, incoming) {
  const ids = new Set(current.map((item) => String(item.id)));
  const result = [...current];
  (Array.isArray(incoming) ? incoming : []).forEach((item) => {
    if (!item || item.id === null || item.id === undefined || ids.has(String(item.id))) return;
    ids.add(String(item.id));
    result.push(item);
  });
  return result;
}

function lastId(items, fallback) {
  const last = Array.isArray(items) && items.length ? items[items.length - 1] : null;
  return last?.id ?? fallback;
}

function SearchHeader() {
  return (
    <header className="busca-topo">
      <nav className="navbar busca-navbar" aria-label="Navegação principal">
        <a className="busca-marca" href="/">
          <img className="navbar__logo" src="/assets/home/logo-palco-branco.png" alt="" />
          <span>Palco</span>
        </a>
        <ul className="navbar__menu">
          <li><a className="navbar__link navbar__link--atual" href="/vagas" aria-current="page">Vagas</a></li>
          <li><a className="navbar__link" href="/#sobre">Empresa</a></li>
        </ul>
        <div className="navbar__acoes">
          <a className="navbar__link" href="/login">Login</a>
          <a className="navbar__link navbar__link--destaque" href="/cadastro">Cadastrar</a>
        </div>
      </nav>
      <div className="busca-hero">
        <p className="busca-hero__sobrelinha">Oportunidades abertas</p>
        <h1>Encontre seu próximo <span>palco</span></h1>
        <p>Pesquise vagas reais sem precisar entrar. Use os filtros para chegar à oportunidade certa.</p>
      </div>
    </header>
  );
}

function FilterForm({ draft, onChange, onSubmit, onClear }) {
  return (
    <aside className="filtros" aria-labelledby="titulo-filtros">
      <div className="filtros__cabecalho">
        <div>
          <p className="filtros__sobrelinha">Refine a busca</p>
          <h2 id="titulo-filtros">Filtros</h2>
        </div>
        <button className="filtros__limpar" type="button" onClick={onClear}>Limpar</button>
      </div>
      <form className="filtros__form" onSubmit={onSubmit}>
        <label className="campo">
          <span className="campo__rotulo">Título</span>
          <input className="campo__input" type="search" name="titulo" autoComplete="off" placeholder="Ex.: cantora" value={draft.titulo || ''} onChange={onChange} />
        </label>
        <label className="campo">
          <span className="campo__rotulo">Empresa ou contratante</span>
          <input className="campo__input" type="search" name="empresa" autoComplete="organization" placeholder="Nome da empresa" value={draft.empresa || ''} onChange={onChange} />
        </label>
        <div className="filtros__dupla">
          <label className="campo">
            <span className="campo__rotulo">Cidade</span>
            <input className="campo__input" type="search" name="cidade" autoComplete="address-level2" placeholder="Cidade" value={draft.cidade || ''} onChange={onChange} />
          </label>
          <label className="campo">
            <span className="campo__rotulo">Estado</span>
            <input className="campo__input" type="text" name="estado" maxLength="2" autoComplete="address-level1" placeholder="UF" value={draft.estado || ''} onChange={onChange} />
          </label>
        </div>
        <label className="campo">
          <span className="campo__rotulo">Modelo de trabalho</span>
          <select className="campo__input" name="modeloTrabalho" value={draft.modeloTrabalho || ''} onChange={onChange}>
            <option value="">Todos</option>
            <option value="PRESENCIAL">Presencial</option>
            <option value="REMOTO">Remoto</option>
            <option value="HIBRIDO">Híbrido</option>
          </select>
        </label>
        <label className="campo">
          <span className="campo__rotulo">Tipo de contrato</span>
          <input className="campo__input" type="search" name="tipoContrato" autoComplete="off" placeholder="Informe como publicado" value={draft.tipoContrato || ''} onChange={onChange} />
        </label>
        <div className="filtros__dupla">
          <label className="campo">
            <span className="campo__rotulo">Remuneração mínima</span>
            <input className="campo__input" type="number" name="faixaSalarialMin" min="0" step="0.01" inputMode="decimal" placeholder="R$ 0" value={draft.faixaSalarialMin || ''} onChange={onChange} />
          </label>
          <label className="campo">
            <span className="campo__rotulo">Remuneração máxima</span>
            <input className="campo__input" type="number" name="faixaSalarialMax" min="0" step="0.01" inputMode="decimal" placeholder="Sem limite" value={draft.faixaSalarialMax || ''} onChange={onChange} />
          </label>
        </div>
        <label className="campo">
          <span className="campo__rotulo">Área de atuação</span>
          <input className="campo__input" type="search" name="areaAtuacao" autoComplete="off" placeholder="Ex.: música" value={draft.areaAtuacao || ''} onChange={onChange} />
        </label>
        <button className="btn btn--primario filtros__aplicar" type="submit">Aplicar filtros</button>
      </form>
    </aside>
  );
}

function SimilarVacancies({ vacancies }) {
  return (
    <section className="vagas-similares-react" aria-labelledby="titulo-vagas-similares">
      <div className="vagas-similares-react__cabecalho">
        <p>Relacionadas por tags</p>
        <h2 id="titulo-vagas-similares">Vagas Similares</h2>
        <p>Com base nas tags da primeira oportunidade exibida.</p>
      </div>
      <div className="vagas-similares-react__lista">
        {vacancies.map((vacancy, index) => (
          <VacancyCard key={vacancy.id} vacancy={vacancy} index={index} />
        ))}
      </div>
    </section>
  );
}

export default function VacancySearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const searchKey = searchParams.toString();
  const appliedFilters = useMemo(
    () => filtersFromParams(new URLSearchParams(searchKey)),
    [searchKey]
  );
  const [draft, setDraft] = useState(appliedFilters);
  const [feed, setFeed] = useState(EMPTY_FEED);
  const [similars, setSimilars] = useState(EMPTY_SIMILARS);
  const [validationError, setValidationError] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);
  const feedRef = useRef(EMPTY_FEED);
  const filtersRef = useRef(appliedFilters);
  const generationRef = useRef(0);
  const loadingGenerationRef = useRef(null);
  const sentinelRef = useRef(null);

  const loadMore = useCallback(async ({ reset = false, generation = generationRef.current } = {}) => {
    const base = reset ? EMPTY_FEED : feedRef.current;
    if (loadingGenerationRef.current === generation || (!base.hasMore && !base.hasMoreCanceladas)) return;

    loadingGenerationRef.current = generation;
    const loadingState = { ...base, loading: true, error: '' };
    feedRef.current = loadingState;
    setFeed(loadingState);

    try {
      const response = await getVacancyPage({
        filters: filtersRef.current,
        cursor: base.cursor,
        cursorCanceladas: base.cursorCanceladas,
        size: PAGE_SIZE,
      });
      if (generation !== generationRef.current) return;

      const openPage = Array.isArray(response.content) ? response.content : [];
      const cancelledPage = Array.isArray(response.vagasCanceladasComCandidatura)
        ? response.vagasCanceladasComCandidatura
        : [];
      const hasMore = response.hasMore === true && response.nextCursor !== null && response.nextCursor !== undefined;
      const hasMoreCanceladas = response.hasMoreCanceladas === true
        && response.nextCursorCanceladas !== null
        && response.nextCursorCanceladas !== undefined;
      const next = {
        open: uniqueById(base.open, openPage),
        cancelled: uniqueById(base.cancelled, cancelledPage),
        cursor: hasMore ? response.nextCursor : lastId(openPage, base.cursor),
        cursorCanceladas: hasMoreCanceladas
          ? response.nextCursorCanceladas
          : lastId(cancelledPage, base.cursorCanceladas),
        hasMore,
        hasMoreCanceladas,
        loading: false,
        error: '',
      };
      feedRef.current = next;
      setFeed(next);
    } catch (error) {
      if (generation !== generationRef.current) return;
      const next = {
        ...base,
        loading: false,
        error: error?.message || 'Não foi possível carregar as vagas agora. Tente novamente.',
      };
      feedRef.current = next;
      setFeed(next);
    } finally {
      if (loadingGenerationRef.current === generation) loadingGenerationRef.current = null;
    }
  }, []);

  useEffect(() => {
    document.body.classList.add('pagina-buscar-vagas');
    document.title = 'Buscar vagas — Palco';
    return () => document.body.classList.remove('pagina-buscar-vagas');
  }, []);

  useEffect(() => {
    const generation = generationRef.current + 1;
    generationRef.current = generation;
    filtersRef.current = appliedFilters;
    setDraft(appliedFilters);
    setValidationError('');
    feedRef.current = EMPTY_FEED;
    setFeed(EMPTY_FEED);
    loadMore({ reset: true, generation });
  }, [appliedFilters, loadMore, refreshKey]);

  const referenceVacancyId = feed.open[0]?.id;

  useEffect(() => {
    if (referenceVacancyId === null || referenceVacancyId === undefined) {
      setSimilars(EMPTY_SIMILARS);
      return undefined;
    }

    let active = true;
    setSimilars({ status: 'loading', referenceId: referenceVacancyId, items: [] });
    getSimilarVacancies(referenceVacancyId, { size: 3 })
      .then((response) => {
        if (!active) return;
        const items = (Array.isArray(response?.content) ? response.content : [])
          .filter((item) => item?.status === 'ABERTA'
            && String(item.id) !== String(referenceVacancyId))
          .slice(0, 3);
        setSimilars({ status: 'success', referenceId: referenceVacancyId, items });
      })
      .catch(() => {
        if (active) setSimilars({ status: 'error', referenceId: referenceVacancyId, items: [] });
      });

    return () => {
      active = false;
    };
  }, [referenceVacancyId]);

  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel || feed.error || (!feed.hasMore && !feed.hasMoreCanceladas)) return undefined;

    if ('IntersectionObserver' in window) {
      const observer = new IntersectionObserver((entries) => {
        if (entries.some((entry) => entry.isIntersecting)) loadMore();
      }, { rootMargin: '500px 0px' });
      observer.observe(sentinel);
      return () => observer.disconnect();
    }

    function onScroll() {
      if (sentinel.getBoundingClientRect().top < window.innerHeight + 400) loadMore();
    }
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, [feed.error, feed.hasMore, feed.hasMoreCanceladas, loadMore]);

  function handleChange(event) {
    const { name, value } = event.target;
    setDraft((current) => ({ ...current, [name]: name === 'estado' ? value.toUpperCase() : value }));
  }

  function applyFilters(event) {
    event.preventDefault();
    if (draft.faixaSalarialMin && draft.faixaSalarialMax
      && Number(draft.faixaSalarialMin) > Number(draft.faixaSalarialMax)) {
      setValidationError('A remuneração mínima não pode ser maior que a máxima.');
      return;
    }

    const params = buildVacancyParams(draft, { size: PAGE_SIZE });
    params.delete('size');
    const nextKey = params.toString();
    setValidationError('');
    if (nextKey === searchKey) setRefreshKey((value) => value + 1);
    else setSearchParams(params, { replace: true });
  }

  function clearFilters() {
    setDraft({});
    setValidationError('');
    if (!searchKey) setRefreshKey((value) => value + 1);
    else setSearchParams({}, { replace: true });
  }

  const hasResults = feed.open.length + feed.cancelled.length > 0;
  const initialLoading = feed.loading && !hasResults;
  const incrementalLoading = feed.loading && hasResults;
  const empty = !feed.loading && !feed.error && !hasResults;
  const finished = hasResults && !feed.loading && !feed.error && !feed.hasMore && !feed.hasMoreCanceladas;
  const visibleSimilars = similars.status === 'success'
    && String(similars.referenceId) === String(referenceVacancyId)
    ? similars.items
    : [];

  return (
    <>
      <SearchHeader />
      <main className="busca-layout">
        <FilterForm draft={draft} onChange={handleChange} onSubmit={applyFilters} onClear={clearFilters} />
        <section className="resultados" aria-labelledby="titulo-resultados">
          <div className="resultados__cabecalho">
            <div>
              <p className="resultados__sobrelinha">Feed público</p>
              <h2 id="titulo-resultados">Vagas abertas</h2>
            </div>
            <p className="resultados__status" role="status" aria-live="polite">
              {feed.open.length} {feed.open.length === 1 ? 'vaga aberta exibida' : 'vagas abertas exibidas'}
            </p>
          </div>

          {validationError ? <div className="estado estado--erro" role="alert"><p>{validationError}</p></div> : null}
          {initialLoading ? (
            <div className="estado estado--carregando" role="status">
              <span className="estado__spinner" aria-hidden="true" />
              <p>Carregando oportunidades…</p>
            </div>
          ) : null}
          {feed.error ? (
            <div className="estado estado--erro" role="alert">
              <h3>Não foi possível carregar as vagas</h3>
              <p>{feed.error}</p>
              <button className="btn busca-botao-secundario" type="button" onClick={() => loadMore()}>
                Tentar novamente
              </button>
            </div>
          ) : null}
          {empty ? (
            <div className="estado">
              <h3>Nenhuma vaga encontrada com esses filtros.</h3>
              <p>Limpe ou ajuste os campos para ampliar a busca.</p>
            </div>
          ) : null}

          {visibleSimilars.length ? <SimilarVacancies vacancies={visibleSimilars} /> : null}

          <div className="vagas-lista">
            {feed.open.map((vacancy, index) => (
              <VacancyCard key={vacancy.id} vacancy={vacancy} index={index} />
            ))}
          </div>

          {feed.cancelled.length ? (
            <section className="canceladas" aria-labelledby="titulo-canceladas">
              <div className="canceladas__cabecalho">
                <p>Histórico da sua candidatura</p>
                <h2 id="titulo-canceladas">Vagas canceladas em que você se candidatou</h2>
              </div>
              <div className="vagas-lista vagas-lista--canceladas">
                {feed.cancelled.map((vacancy, index) => (
                  <VacancyCard key={vacancy.id} vacancy={vacancy} index={index} cancelled />
                ))}
              </div>
            </section>
          ) : null}

          {incrementalLoading ? (
            <div className="estado estado--mais" role="status">
              <span className="estado__spinner" aria-hidden="true" />
              <p>Carregando mais vagas…</p>
            </div>
          ) : null}
          {finished ? <p className="resultados__fim">Você chegou ao fim das vagas disponíveis.</p> : null}
          <div className="resultados__sentinela" ref={sentinelRef} aria-hidden="true" />
        </section>
      </main>
      <BackToTopButton />
      <footer className="busca-rodape">
        <a href="/">Palco</a>
        <p>Artistas encontram oportunidades. Contratantes encontram arte.</p>
      </footer>
    </>
  );
}

export { EMPTY_FEED, EMPTY_SIMILARS, PAGE_SIZE, filtersFromParams, uniqueById };
