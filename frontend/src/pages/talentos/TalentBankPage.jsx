import { useEffect, useState } from 'react';
import AccountLayout from '../../components/account/AccountLayout';
import SaveButton from '../../components/salvos/SaveButton';
import { getTalents, getTalentCatalog, talentError } from '../../services/talentos/talentService';
import './talent-bank.css';

const EMPTY_FILTERS = { areaId: '', funcaoIds: [], especializacaoIds: [], localizacao: '', raios: [],
  experienciaMinima: '', disponivel: '', tipos: [], vagaId: '', ordenacao: 'RELEVANCIA' };
const LEVELS = ['SEM_EXPERIENCIA', 'INICIANTE', 'INTERMEDIARIO', 'EXPERIENTE', 'ESPECIALISTA'];
const RANGES = ['LOCAL', 'REGIONAL', 'NACIONAL', 'INTERNACIONAL', 'REMOTO'];
const TYPES = ['ARTISTA_SOLO', 'DUPLA', 'BANDA', 'GRUPO_ARTISTICO', 'ESTUDIO', 'PRODUTORA_EMPRESA'];
const label = (value) => value.replaceAll('_', ' ').toLowerCase();
const toggle = (values, value) => values.includes(value) ? values.filter((v) => v !== value) : [...values, value];

function Pagination({ page, hasMore, loading, onPage, name }) {
  return <nav className="talent-pagination" aria-label={name}>
    <button type="button" disabled={loading || page === 0} onClick={() => onPage(page - 1)}>Anterior</button>
    <span>Página {page + 1}</span>
    <button type="button" disabled={loading || !hasMore} onClick={() => onPage(page + 1)}>Próxima</button>
  </nav>;
}

function CatalogOptions({ title, resource, areaId, funcaoIds, value, onChange, multiple = false, disabled = false, disabledMessage = 'Selecione os critérios anteriores.' }) {
  const [page, setPage] = useState(0);
  const [state, setState] = useState({ loading: true, data: null, error: '' });
  const functionsKey = (funcaoIds || []).join(',');
  useEffect(() => {
    if (disabled) return undefined;
    const controller = new AbortController();
    let current = true;
    setState({ loading: true, data: null, error: '' });
    getTalentCatalog(resource, { areaId, funcaoIds: functionsKey, page }, { signal: controller.signal })
      .then((data) => { if (current) setState({ loading: false, data, error: '' }); })
      .catch((error) => { if (current && error.name !== 'AbortError') setState({ loading: false, data: null, error: talentError(error) }); });
    return () => { current = false; controller.abort(); };
  }, [resource, areaId, functionsKey, page, disabled]);
  return <fieldset className="talent-options" disabled={disabled}>
    <legend>{title}</legend>
    {disabled ? <p>{disabledMessage}</p> : <>
      {state.loading ? <p role="status">Carregando {title.toLowerCase()}…</p> : null}
      {state.error ? <p role="alert">{state.error}</p> : null}
      {!multiple ? <label><input type="radio" name={resource} checked={!value} onChange={() => onChange('', null)} />Sem filtro</label> : null}
      {state.data?.content.map((item) => <label key={item.id}>
        <input type={multiple ? 'checkbox' : 'radio'} name={resource}
          checked={multiple ? value.includes(String(item.id)) : String(value) === String(item.id)}
          onChange={() => onChange(multiple ? toggle(value, String(item.id)) : String(item.id), item)} />
        {item.nome || item.titulo}
      </label>)}
      {state.data && !state.data.content.length ? <p>Nenhuma opção disponível.</p> : null}
      {state.data && (page > 0 || state.data.hasMore) ? <Pagination page={page} hasMore={state.data.hasMore}
        loading={state.loading} onPage={setPage} name={'Páginas de ' + title.toLowerCase()} /> : null}
      {multiple && value.length ? <p>{value.length} selecionada(s), incluindo outras páginas. <button type="button" onClick={() => onChange([])}>Limpar seleção</button></p> : null}
    </>}
  </fieldset>;
}

function Choices({ title, options, value, onChange }) {
  return <fieldset className="talent-options"><legend>{title}</legend>
    {options.map((option) => <label key={option}><input type="checkbox" checked={value.includes(option)}
      onChange={() => onChange(toggle(value, option))} />{label(option)}</label>)}
  </fieldset>;
}

function TalentResults({ title, query, onPage }) {
  const [state, setState] = useState({ loading: true, data: null, error: '', status: null });
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let current = true;
    setState({ loading: true, data: null, error: '', status: null });
    getTalents(query, { signal: controller.signal })
      .then((data) => { if (current) setState({ loading: false, data, error: '', status: null }); })
      .catch((error) => { if (current && error.name !== 'AbortError') setState({ loading: false, data: null, error: talentError(error), status: error.status }); });
    return () => { current = false; controller.abort(); };
  }, [query, retry]);
  return <section className="account-panel talent-section" aria-label={title} aria-busy={state.loading}>
    <h2>{title}</h2>
    {state.loading ? <p role="status">Carregando talentos…</p> : null}
    {state.error ? <div role="alert"><p>{state.error}</p>{state.status === 401 ? <a href="/login">Entrar novamente</a>
      : <button type="button" onClick={() => setRetry((v) => v + 1)}>Tentar novamente</button>}</div> : null}
    {state.data?.contexto ? <p>Contexto: {state.data.contexto.titulo}</p> : null}
    {state.data && query.recomendados && !state.data.contexto ? <p>Publique uma vaga para receber recomendações. Você já pode buscar pelos filtros abaixo.</p> : null}
    {state.data && !state.data.content.length ? <p>Nenhum artista corresponde aos critérios.</p> : null}
    {state.data?.content.length ? <p>{state.data.totalElements} artista(s). Compatibilidade: funções em comum, especializações e atualização recente.</p> : null}
    <div className="talent-grid">
      {state.data?.content.map((talent) => <article className="talent-card" key={talent.artistaId}>
        <header>{/^(https?:\/\/|\/)/i.test(talent.avatarUrl || '') ? <img src={talent.avatarUrl} alt="" loading="lazy" /> : null}
          <div><h3>{talent.nomeExibicao}</h3><p>{talent.localizacao || 'Localização não informada'}</p></div></header>
        <p>{talent.biografia}</p>
        <p>{label(talent.tipoPerfilArtistico || 'Tipo não informado')} · {label(talent.raioAtuacao || 'Abrangência não informada')}</p>
        <p><strong>Disponibilidade: </strong>{talent.disponivelOportunidades === true ? 'Sim' : talent.disponivelOportunidades === false ? 'Não' : 'Não informada'}</p>
        {talent.areas?.map((area) => <div key={area.id} className="talent-taxonomy">
          <h4>{area.nome}</h4><p>Experiência: {label(area.nivelExperiencia || 'Não informada')}</p>
          <p>Funções: {area.funcoes.map((f) => f.nome).join(', ') || 'Não informadas'}</p>
          <p>Especializações: {area.especializacoes.map((e) => e.nome).join(', ') || 'Não informadas'}</p>
        </div>)}
        <p>{talent.quantidadeFuncoesCoincidentes} função(ões) e {talent.quantidadeEspecializacoesCoincidentes} especialização(ões) em comum</p>
        <a href={'/perfis/ARTISTA/' + encodeURIComponent(talent.artistaId)}>Ver Perfil</a>
        <SaveButton tipoAlvo="PERFIL_ARTISTA" alvoId={talent.artistaId} nome={talent.nomeExibicao} />
      </article>)}
    </div>
    {state.data && state.data.totalElements > 0 ? <Pagination page={state.data.page} hasMore={state.data.hasMore}
      loading={state.loading} onPage={onPage} name={'Páginas de ' + title.toLowerCase()} /> : null}
  </section>;
}

export default function TalentBankPage() {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [recommended, setRecommended] = useState({ recomendados: true, page: 0, size: 20 });
  const [search, setSearch] = useState(null);
  const set = (name, value) => setFilters((f) => ({ ...f, [name]: value }));
  const changeArea = (areaId) => setFilters((f) => ({ ...f, areaId, funcaoIds: [], especializacaoIds: [], experienciaMinima: '' }));
  return <AccountLayout><main className="account-main talent-main">
    <div className="account-heading"><div><p className="account-eyebrow">Descubra profissionais</p>
      <h1>Banco de Talentos</h1><p>Encontre artistas pelas áreas e competências que sua produção precisa.</p><a href="#talent-filters">Ir aos filtros</a></div></div>
    <TalentResults title="Recomendados para você" query={recommended} onPage={(page) => setRecommended((q) => ({ ...q, page }))} />
    <form id="talent-filters" className="account-panel talent-section" onSubmit={(e) => { e.preventDefault(); setSearch({ ...filters, page: 0, size: 20 }); }} aria-label="Filtros de talentos">
      <h2>Buscar artistas</h2><p>Várias opções no mesmo filtro aceitam qualquer uma delas. Critérios diferentes são combinados.</p>
      <div className="talent-filters">
        <CatalogOptions title="Vaga de contexto" resource="contextos" value={filters.vagaId} onChange={(value, item) => {
          setFilters((f) => ({ ...f, vagaId: value, areaId: item ? String(item.areaId) : '', funcaoIds: [], especializacaoIds: [], experienciaMinima: '' }));
        }} />
        <CatalogOptions title="Área artística" resource="areas" value={filters.areaId} disabled={Boolean(filters.vagaId)} disabledMessage="Área definida pela vaga de contexto selecionada." onChange={changeArea} />
        <CatalogOptions key={'funcoes-' + filters.areaId} title="Funções" resource="funcoes" multiple
          areaId={filters.areaId} value={filters.funcaoIds} disabled={!filters.areaId}
          onChange={(value) => setFilters((f) => ({ ...f, funcaoIds: value, especializacaoIds: [] }))} />
        <CatalogOptions key={'especializacoes-' + filters.areaId + '-' + filters.funcaoIds.join(',')} title="Especializações" resource="especializacoes" multiple
          areaId={filters.areaId} funcaoIds={filters.funcaoIds} value={filters.especializacaoIds}
          disabled={!filters.areaId || !filters.funcaoIds.length} onChange={(v) => set('especializacaoIds', v)} />
        <label className="talent-field">Localização informada no perfil<input maxLength={150} value={filters.localizacao} onChange={(e) => set('localizacao', e.target.value)} />
          <small>Busca por texto. Filtros exatos de cidade e Estado ainda não estão disponíveis.</small></label>
        <label className="talent-field">Experiência mínima<select value={filters.experienciaMinima} disabled={!filters.areaId} onChange={(e) => set('experienciaMinima', e.target.value)}>
          <option value="">Qualquer experiência</option>{LEVELS.map((v) => <option key={v} value={v}>{label(v)}</option>)}</select><small>Avaliada na área selecionada.</small></label>
        <label className="talent-field">Disponível para oportunidades<select value={filters.disponivel} onChange={(e) => set('disponivel', e.target.value)}>
          <option value="">Qualquer disponibilidade</option><option value="true">Sim</option><option value="false">Não</option></select></label>
        <label className="talent-field">Ordenação<select value={filters.ordenacao} onChange={(e) => set('ordenacao', e.target.value)}>
          <option value="RELEVANCIA">Compatibilidade profissional</option><option value="ATUALIZACAO">Atualização recente</option></select></label>
        <Choices title="Raio de atuação" options={RANGES} value={filters.raios} onChange={(v) => set('raios', v)} />
        <Choices title="Tipo de perfil artístico" options={TYPES} value={filters.tipos} onChange={(v) => set('tipos', v)} />
      </div>
      <div className="talent-actions"><button className="btn btn--primario" type="submit">Buscar artistas</button>
        <button type="button" onClick={() => { setFilters(EMPTY_FILTERS); setSearch(null); }}>Limpar filtros</button></div>
    </form>
    {search ? <TalentResults title="Resultados da busca" query={search} onPage={(page) => setSearch((q) => ({ ...q, page }))} /> : null}
  </main></AccountLayout>;
}
