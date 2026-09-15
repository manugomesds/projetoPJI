import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PublicNavigation } from '../auth/UserTypePage';
import SaveButton from '../../components/salvos/SaveButton';
import { getSavedItems } from '../../services/salvos/savedService';

export default function SavedItemsPage() {
  const [params, setParams] = useSearchParams();
  const type = ['PERFIL_ARTISTA', 'VAGA'].includes(params.get('tipoAlvo')) ? params.get('tipoAlvo') : '';
  const page = /^\d+$/.test(params.get('page') || '') ? Number(params.get('page')) : 0;
  const [retry, setRetry] = useState(0);
  const [state, setState] = useState({ loading: true, data: null, error: null });
  useEffect(() => { document.title = 'Meus Salvos — Palco'; }, []);
  useEffect(() => {
    const refresh = () => setRetry(value => value + 1);
    window.addEventListener('pageshow', refresh);
    return () => window.removeEventListener('pageshow', refresh);
  }, []);
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    setState({ loading: true, data: null, error: null });
    getSavedItems({ tipoAlvo: type, page }, { signal: controller.signal })
      .then(data => { if (active) setState({ loading: false, data, error: null }); })
      .catch(error => { if (active && error.name !== 'AbortError') setState({ loading: false, data: null, error }); });
    return () => { active = false; controller.abort(); };
  }, [type, page, retry]);
  function navigate(nextType, nextPage) {
    const query = new URLSearchParams();
    if (nextType) query.set('tipoAlvo', nextType);
    if (nextPage) query.set('page', String(nextPage));
    setParams(query);
  }
  function removed(next) {
    if (next.salvo) return;
    if (page > 0 && state.data?.content.length === 1) navigate(type, page - 1);
    else setRetry(value => value + 1);
  }
  return <div className="saved-page"><PublicNavigation /><main className="saved-main">
    <header className="saved-heading"><div><span className="saved-eyebrow">Sua seleção</span><h1>Meus Salvos</h1><p>Perfis e oportunidades para encontrar de novo, no seu tempo.</p></div><a className="saved-button" href="/vagas">Explorar vagas</a></header>
    <nav className="saved-tabs" aria-label="Filtrar salvos">{[['', 'Todos'], ['PERFIL_ARTISTA', 'Perfis'], ['VAGA', 'Vagas']].map(([value, label]) =>
      <button type="button" key={value} aria-pressed={type === value} onClick={() => navigate(value, 0)}>{label}</button>)}</nav>
    {state.loading ? <p role="status">Carregando seus salvos…</p> : null}
    {state.error ? <div role="alert" className="saved-empty"><p>{state.error.message || 'Não foi possível carregar seus salvos.'}</p>
      {state.error.status === 401 ? <a href="/login">Entrar novamente</a> : <button className="saved-button" onClick={() => setRetry(value => value + 1)}>Tentar novamente</button>}</div> : null}
    {state.data ? <>
      <p aria-live="polite">{state.data.totalElements} item(ns) salvo(s)</p>
      {!state.data.content.length ? <div className="saved-empty"><h2>Nenhum salvo por aqui ainda</h2><p>Use a estrela nos perfis e nas vagas para guardar o que chamou sua atenção.</p></div> : null}
      <div className="saved-grid">{state.data.content.map(item => <article className="saved-card" key={`${item.tipoAlvo}/${item.alvoId}`}>
        <header>{/^https?:\/\//i.test(item.avatarUrl || '') ? <img src={item.avatarUrl} alt="" /> : null}<div><span className="saved-type">{item.tipoAlvo === 'PERFIL_ARTISTA' ? 'Perfil de artista' : 'Vaga'}</span><h2>{item.nome}</h2></div></header>
        {item.nomeContratante ? <p>{item.nomeContratante}</p> : null}{item.localizacao ? <p>{item.localizacao}</p> : null}
        {item.funcoes ? <p>Funções: {item.funcoes}</p> : null}
        {item.status ? <p>Status: {item.status.toLowerCase()}</p> : null}
        {item.disponivel && /^\/(perfis\/ARTISTA|vagas)\/[1-9]\d*$/.test(item.href || '') ? <a href={item.href}>Abrir {item.tipoAlvo === 'PERFIL_ARTISTA' ? 'perfil' : 'vaga'}</a> : <p>Indisponível para abertura. Você pode remover este item dos salvos.</p>}
        <SaveButton tipoAlvo={item.tipoAlvo} alvoId={item.alvoId} nome={item.nome} initialSaved={true} onChange={removed} />
      </article>)}</div>
      <nav className="saved-pagination" aria-label="Páginas de salvos"><button className="saved-button" disabled={!state.data.hasPrevious} onClick={() => navigate(type, page - 1)}>Anterior</button>
        <span>Página {page + 1} de {Math.max(1, state.data.totalPages)}</span><button className="saved-button" disabled={!state.data.hasNext} onClick={() => navigate(type, page + 1)}>Próxima</button></nav>
    </> : null}
  </main></div>;
}
