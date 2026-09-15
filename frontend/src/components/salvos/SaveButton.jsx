import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import sessionService from '../../auth/sessionService';
import { getSavedState, saveItem, removeSavedItem, publishSavedState, savedKey, SAVED_EVENT } from '../../services/salvos/savedService';
import '../../styles/salvos.css';

export default function SaveButton({ tipoAlvo, alvoId, nome, initialSaved, initialCount = 0, showCount = false, onChange }) {
  const token = sessionService.getAccessToken();
  const valid = /^(PERFIL_ARTISTA|VAGA)$/.test(tipoAlvo) && /^[1-9]\d*$/.test(String(alvoId));
  const [state, setState] = useState({ salvo: initialSaved ?? null, quantidadeSalvos: initialCount });
  const [loading, setLoading] = useState(Boolean(token && initialSaved === undefined));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(false);
  const pending = useRef(false), trigger = useRef(null), dialog = useRef(null);
  const titleId = useId();
  const item = tipoAlvo === 'PERFIL_ARTISTA' ? 'perfil' : 'vaga';
  const key = savedKey(tipoAlvo, alvoId);
  const change = useRef(onChange);
  change.current = onChange;

  useEffect(() => {
    if (!valid || !token) return undefined;
    let active = true;
    async function refresh() {
      setLoading(true);
      try { const next = await getSavedState(tipoAlvo, alvoId); if (active) { setState(next); setError(''); } }
      catch (err) { if (active) setError(err.message || 'Não foi possível consultar seus salvos.'); }
      finally { if (active) setLoading(false); }
    }
    if (initialSaved === undefined) refresh();
    function changed(event) {
      if (event.detail?.key === key) { setState(event.detail.state); change.current?.(event.detail.state); }
    }
    window.addEventListener('pageshow', refresh);
    window.addEventListener(SAVED_EVENT, changed);
    return () => { active = false; window.removeEventListener('pageshow', refresh); window.removeEventListener(SAVED_EVENT, changed); };
  }, [tipoAlvo, alvoId, token, key, valid, initialSaved]);

  useEffect(() => {
    if (!open) return undefined;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialog.current?.querySelector('button')?.focus();
    const button = trigger.current;
    return () => { document.body.style.overflow = overflow; button?.focus(); };
  }, [open]);

  async function mutate() {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError('');
    try {
      let next;
      if (state.salvo) {
        await removeSavedItem(tipoAlvo, alvoId);
        next = { salvo: false, quantidadeSalvos: null };
        try { next = await getSavedState(tipoAlvo, alvoId); } catch { /* Remoção confirmada; contador não estimado. */ }
      } else {
        next = await saveItem(tipoAlvo, alvoId);
        if (next?.salvo !== true) throw new Error('O servidor não confirmou o salvamento.');
      }
      setState(next); publishSavedState(tipoAlvo, alvoId, next); setOpen(false);
    } catch (err) { setError(err.message || 'Não foi possível atualizar o salvo. Tente novamente.'); setOpen(false); }
    finally { pending.current = false; setBusy(false); }
  }
  async function activate() {
    if (state.salvo === null) {
      setLoading(true); setError('');
      try { setState(await getSavedState(tipoAlvo, alvoId)); } catch (err) { setError(err.message); }
      finally { setLoading(false); }
    } else if (state.salvo) await mutate(); else setOpen(true);
  }
  function keyDown(event) {
    if (event.key === 'Escape' && !pending.current) setOpen(false);
    if (event.key !== 'Tab') return;
    const buttons = [...dialog.current.querySelectorAll('button:not(:disabled)')];
    if (!buttons.length) { event.preventDefault(); return; }
    if (event.shiftKey && document.activeElement === buttons[0]) { event.preventDefault(); buttons.at(-1).focus(); }
    else if (!event.shiftKey && document.activeElement === buttons.at(-1)) { event.preventDefault(); buttons[0].focus(); }
  }
  if (!valid) return null;
  const icon = <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path d="m12 2 3.1 6.3 6.9 1-5 4.9 1.2 6.8-6.2-3.2L5.8 21 7 14.2 2 9.3l6.9-1Z" fill={state.salvo && token ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" /></svg>;
  return <div className="saved-action">
    {token ? <button ref={trigger} className={`saved-button${state.salvo ? ' saved-button--active' : ''}`} type="button"
      aria-pressed={state.salvo === true} disabled={loading || busy}
      aria-label={state.salvo ? `Remover ${item} ${nome || ''} dos salvos` : `Salvar ${item} ${nome || ''}`}
      onClick={activate}>{icon}<span>{busy ? 'Atualizando…' : loading ? 'Carregando…' : state.salvo ? 'Salvo' : state.salvo === null ? 'Tentar novamente' : `Salvar ${item}`}</span></button>
      : <a className="saved-button" href="/login" aria-label={`Entrar para salvar ${item}`}>{icon}<span>Salvar {item}</span></a>}
    {showCount ? <span className="saved-count" aria-live="polite">{typeof state.quantidadeSalvos === 'number' ? `${state.quantidadeSalvos} salvamentos` : 'Contagem indisponível'}</span> : null}
    {error ? <p className="saved-error" role="alert">{error}</p> : null}
    {open ? createPortal(<div className="management-modal-overlay saved-overlay" onMouseDown={event => {
      if (event.target === event.currentTarget && !busy) setOpen(false);
    }}><section ref={dialog} className="management-modal saved-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId} onKeyDown={keyDown}>
      <h2 id={titleId}>Salvar {item === 'perfil' ? 'este perfil' : 'esta vaga'}?</h2>
      <p>{nome || (item === 'perfil' ? 'Este perfil' : 'Esta vaga')} ficará em Meus Salvos para você consultar depois.</p>
      <div className="management-modal__actions"><button type="button" className="saved-button" disabled={busy} onClick={() => setOpen(false)}>Cancelar</button>
        <button type="button" className="saved-button saved-button--confirm" disabled={busy} onClick={mutate}>{busy ? 'Salvando…' : 'Confirmar salvamento'}</button></div>
    </section></div>, document.body) : null}
  </div>;
}
