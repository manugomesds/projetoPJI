import { useEffect, useRef, useState } from 'react';
import { addVideo, deleteItem, listPortfolio, PORTFOLIO_LIMITS, privateContent, publicContentUrl, safeEmbedUrl, uploadFile, validateFile } from '../../services/portfolio/portfolioService';
import './portfolio.css';

function FileCard({ item, owner, busy, onDelete }) {
  const [url, setUrl] = useState(owner ? null : publicContentUrl(item.id));
  const [state, setState] = useState({ loading: false, error: '' });
  const pending = useRef(false);
  const resources = useRef({ controller: null, url: null, active: true });
  useEffect(() => {
    const resource = resources.current; resource.active = true;
    return () => { resource.active = false; resource.controller?.abort(); if (resource.url) URL.revokeObjectURL(resource.url); };
  }, []);
  async function load() {
    if (pending.current) return;
    pending.current = true;
    setState({ loading: true, error: '' });
    const resource = resources.current;
    resource.controller = new AbortController();
    try {
      const blob = await privateContent(item.id, resource.controller.signal);
      if (!resource.active) return;
      resource.url = URL.createObjectURL(blob); setUrl(resource.url);
      setState({ loading: false, error: '' });
    } catch (error) {
      if (resource.active) setState({ loading: false, error: error.message || 'Não foi possível carregar o arquivo.' });
    } finally { pending.current = false; }
  }
  return <article className="portfolio-item">
    <h4>{item.nomeOriginal}</h4><p>{item.tipoMime} · {(item.tamanhoBytes / 1024 / 1024).toFixed(2)} MB</p>
    {owner && !url ? <button type="button" onClick={load} disabled={state.loading}>{state.loading ? 'Carregando arquivo…' : item.tipo === 'PDF' ? 'Preparar download' : 'Carregar prévia'}</button> : null}
    {url && item.tipo === 'IMAGEM' ? <img src={url} alt={item.nomeOriginal} loading="lazy" /> : null}
    {item.tipo === 'AUDIO' ? <audio src={url || undefined} controls preload="none" aria-label={`Áudio ${item.nomeOriginal}`} /> : null}
    {url && item.tipo === 'PDF' ? <a href={url} download={item.nomeOriginal} target="_blank" rel="noopener noreferrer">Baixar PDF: {item.nomeOriginal}</a> : null}
    {state.error ? <p role="alert">{state.error}</p> : null}
    {owner ? <button type="button" onClick={() => onDelete('arquivos', item.id)} disabled={busy} aria-label={`Excluir ${item.nomeOriginal}`}>Excluir arquivo</button> : null}
  </article>;
}

function Pager({ data, value, onChange, busy, label }) {
  return <nav className="portfolio-pagination" aria-label={`Paginação de ${label}`}>
    <button type="button" disabled={busy || value === 0} onClick={() => onChange(value - 1)}>Anterior</button>
    <span>Página {value + 1}</span>
    <button type="button" disabled={busy || !data?.hasMore} onClick={() => onChange(value + 1)}>Próxima</button>
  </nav>;
}

export default function PortfolioSection({ owner = false, artistId }) {
  const [page, setPage] = useState(0), [videoPage, setVideoPage] = useState(0), [version, setVersion] = useState(0);
  const [files, setFiles] = useState(null), [videos, setVideos] = useState(null);
  const [file, setFile] = useState(null), [video, setVideo] = useState('');
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false);
  const [error, setError] = useState(''), [success, setSuccess] = useState('');
  const input = useRef(null), pending = useRef(false);
  useEffect(() => {
    const controller = new AbortController(); let active = true;
    setLoading(true);
    Promise.all([listPortfolio(owner, artistId, 'arquivos', page, controller.signal), listPortfolio(owner, artistId, 'videos', videoPage, controller.signal)])
      .then(([f, v]) => { if (active) { setFiles(f); setVideos(v); setLoading(false); } })
      .catch(e => { if (active) { setFiles(null); setVideos(null); setError(e.message || 'Não foi possível carregar o portfólio.'); setLoading(false); } });
    return () => { active = false; controller.abort(); };
  }, [owner, artistId, page, videoPage, version]);

  async function mutate(action, message) {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError(''); setSuccess('');
    try {
      await action(); setSuccess(message); setPage(0); setVideoPage(0); setVersion(v => v + 1);
    } catch (e) { setError(e.message || 'Não foi possível atualizar o portfólio.'); }
    finally { pending.current = false; setBusy(false); }
  }
  function sendFile(event) {
    event.preventDefault(); const message = validateFile(file);
    if (message) { setError(message); setSuccess(''); return; }
    mutate(async () => { await uploadFile(file); setFile(null); if (input.current) input.current.value = ''; }, 'Arquivo enviado com sucesso.');
  }
  function remove(kind, id) { mutate(() => deleteItem(kind, id), 'Item excluído com sucesso.'); }
  const blocked = busy || loading;
  const fileItems = Array.isArray(files?.content) ? files.content : [];
  const videoItems = Array.isArray(videos?.content) ? videos.content : [];
  return <section className="portfolio-section" aria-label="Portfólio multimídia">
    <header className="portfolio-heading"><p className="portfolio-eyebrow">Trabalhos & criações</p><h2>Portfólio</h2><p>Uma seleção de imagens, sons e vídeos que dão vida ao trabalho artístico.</p></header>
    {owner ? <>
      <p>Os arquivos integram seu perfil público quando a exposição é permitida. Perfis de menores permanecem privados.</p>
      <form onSubmit={sendFile} aria-label="Enviar arquivo ao portfólio">
        <fieldset disabled={busy}>
          <label htmlFor="portfolio-file">Arquivo do portfólio</label>
          <p id="portfolio-limits">{PORTFOLIO_LIMITS}</p>
          <input ref={input} id="portfolio-file" type="file" accept=".jpg,.jpeg,.png,.pdf,.mp3" aria-describedby={`portfolio-limits${error ? ' portfolio-error' : ''}`} aria-invalid={Boolean(error)} onChange={e => { const selected = e.target.files?.[0]; setFile(selected || null); setError(validateFile(selected)); setSuccess(''); }} />
          <button type="submit" disabled={!file || Boolean(validateFile(file))}>{busy ? 'Enviando…' : 'Enviar arquivo'}</button>
        </fieldset>
      </form>
      <form onSubmit={e => { e.preventDefault(); mutate(async () => { await addVideo(video); setVideo(''); }, 'Vídeo adicionado com sucesso.'); }} aria-label="Adicionar vídeo ao portfólio">
        <fieldset disabled={busy}><label htmlFor="portfolio-video">Link YouTube ou Vimeo</label>
          <input id="portfolio-video" type="url" value={video} maxLength={255} required placeholder="https://www.youtube.com/watch?v=…" onChange={e => setVideo(e.target.value)} />
          <button type="submit" disabled={!video.trim()}>Adicionar vídeo</button>
        </fieldset>
      </form>
    </> : null}
    {error ? <p id="portfolio-error" role="alert">{error}</p> : null}
    {success ? <p role="status">{success}</p> : null}
    {loading ? <p role="status">Carregando portfólio…</p> : null}
    {busy ? <p role="status">Processando alteração…</p> : null}
    {!loading && !files ? <button type="button" onClick={() => { setError(''); setVersion(v => v + 1); }}>Tentar novamente</button> : null}
    <h3>Arquivos</h3>
    {!loading && files && !fileItems.length ? <p>Nenhum arquivo no portfólio.</p> : null}
    {!loading ? <div className="portfolio-grid">{fileItems.map(item => <FileCard key={`${owner}-${item.id}`} item={item} owner={owner} busy={blocked} onDelete={remove} />)}</div> : null}
    <Pager data={files} value={page} onChange={setPage} busy={blocked} label="arquivos" />
    <h3>Vídeos</h3>
    {!loading && videos && !videoItems.length ? <p>Nenhum vídeo no portfólio.</p> : null}
    {!loading ? <div className="portfolio-grid">{videoItems.map(item => {
      const url = safeEmbedUrl(item.embedUrl);
      return <article className="portfolio-item" key={item.id}>
        <h4>Vídeo {item.provedor === 'VIMEO' ? 'Vimeo' : 'YouTube'}</h4>
        {url ? <><iframe src={url} title={`Vídeo do portfólio ${item.id}`} loading="lazy" allow="picture-in-picture" allowFullScreen sandbox="allow-scripts allow-same-origin allow-presentation" referrerPolicy="strict-origin-when-cross-origin" />
          <p>Se o player estiver indisponível, <a href={url} target="_blank" rel="noopener noreferrer">abrir vídeo na plataforma</a>.</p></> : <p>Vídeo indisponível.</p>}
        {owner ? <button type="button" onClick={() => remove('videos', item.id)} disabled={blocked} aria-label={`Excluir vídeo ${item.id}`}>Excluir vídeo</button> : null}
      </article>;
    })}</div> : null}
    <Pager data={videos} value={videoPage} onChange={setVideoPage} busy={blocked} label="vídeos" />
  </section>;
}
