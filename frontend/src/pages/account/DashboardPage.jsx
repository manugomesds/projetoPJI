import { useEffect, useState } from 'react';
import AccountLayout from '../../components/account/AccountLayout';
import NotificationPanel from '../../components/notifications/NotificationPanel';
import { getDashboard } from '../../services/account/accountService';

function money(value) {
  if (value == null) return 'Valor não informado';
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
}

function Availability({ title, data, href, action }) {
  if (!data) return null;
  return (
    <article className="account-module">
      <div className="account-module__top">
        <h2>{title}</h2>
        {typeof data.quantidadeNaoLidas === 'number'
          ? <span className="account-count" aria-label={`${data.quantidadeNaoLidas} não lidas`}>{data.quantidadeNaoLidas}</span>
          : null}
      </div>
      <p>{data.mensagem}</p>
      {data.disponivel && href ? <a href={href}>{action}</a> : <span>{data.disponivel ? 'Disponível' : 'Indisponível'}</span>}
    </article>
  );
}

function Tags({ tags = [], matches }) {
  return (
    <div className="account-tags">
      {typeof matches === 'number' ? <span className="account-tag account-tag--match">{matches} função(ões) em comum</span> : null}
      {tags.map((tag) => <span className="account-tag" key={tag.id}>{tag.nome}</span>)}
    </div>
  );
}

function ArtistDashboard({ data }) {
  const section = data.vagasRecomendadas || { content: [], totalElements: 0, hasMore: false };
  return (
    <>
      {!data.perfilCompleto ? (
        <section className="account-alert" role="status">
          <div><strong>Complete seu perfil</strong><p>Biografia, localização, portfólio e ao menos uma área são necessários para usar todos os recursos.</p></div>
          <a className="btn btn--primario" href="/perfil">Editar perfil</a>
        </section>
      ) : null}
      <section className="account-panel account-panel--wide" aria-labelledby="recommended-title">
        <header><h2 id="recommended-title">Vagas recomendadas</h2><p>Oportunidades abertas ordenadas pelas áreas do seu perfil.</p></header>
        <div className="account-list">
          {section.content.length === 0 ? <p className="account-empty">Nenhuma vaga compatível no momento.</p> : null}
          {section.content.map((vaga) => (
            <article className="account-card" key={vaga.id}>
              <div><h3>{vaga.titulo}</h3><p>{vaga.nomeContratante} · {vaga.cidade}/{vaga.estado} · {money(vaga.remuneraValor)}</p><Tags tags={vaga.funcoes} matches={vaga.quantidadeFuncoesCoincidentes} /></div>
              <a href={`/vagas/${encodeURIComponent(vaga.id)}`}>Ver vaga</a>
            </article>
          ))}
        </div>
        {section.hasMore ? <a className="account-more" href="/vagas">Explorar mais vagas</a> : null}
      </section>
    </>
  );
}

function ContractorDashboard({ data }) {
  const applications = data.candidaturasRecentes || { content: [], totalElements: 0, hasMore: false };
  const talents = data.talentosSugeridos || { content: [], totalElements: 0, hasMore: false };
  return (
    <>
      <section className="account-panel" aria-labelledby="applications-title">
        <header><h2 id="applications-title">Candidaturas recentes</h2><p>Inscrições recebidas nas suas vagas abertas ou pausadas.</p></header>
        <div className="account-list">
          {applications.content.length === 0 ? <p className="account-empty">Nenhuma candidatura recente em vaga ativa.</p> : null}
          {applications.content.map((application) => (
            <article className="account-card" key={application.id}>
              <img src={application.avatarUrl} alt="" />
              <div><h3>{application.nomeArtista}</h3><p>{application.tituloVaga} · {String(application.status).replaceAll('_', ' ')}</p></div>
              <a href={`/perfis/ARTISTA/${encodeURIComponent(application.artistaId)}`}>Ver perfil</a>
            </article>
          ))}
        </div>
      </section>
      <section className="account-panel" aria-labelledby="talents-title">
        <header><h2 id="talents-title">Talentos sugeridos</h2><p>Artistas completos e compatíveis com suas vagas ativas.</p></header>
        <div className="account-list">
          {talents.content.length === 0 ? <p className="account-empty">Nenhum talento compatível no momento.</p> : null}
          {talents.content.map((talent) => (
            <article className="account-card" key={talent.artistaId}>
              <img src={talent.avatarUrl} alt="" />
              <div><h3>{talent.nomeExibicao}</h3><p>{talent.localizacao || 'Localização não informada'}</p><Tags tags={talent.funcoes} matches={talent.quantidadeFuncoesCoincidentes} /></div>
              <a href={`/perfis/ARTISTA/${encodeURIComponent(talent.artistaId)}`}>Ver perfil</a>
            </article>
          ))}
        </div>
      </section>
    </>
  );
}

export default function DashboardPage() {
  const [state, setState] = useState({ loading: true, data: null, error: '' });

  useEffect(() => {
    let active = true;
    getDashboard()
      .then((data) => { if (active) setState({ loading: false, data, error: '' }); })
      .catch(() => { if (active) setState({ loading: false, data: null, error: 'Não foi possível carregar o painel. Tente novamente.' }); });
    return () => { active = false; };
  }, []);

  const { loading, data, error } = state;
  if (loading) return <AccountLayout><main className="account-main"><section className="account-state" aria-live="polite">Carregando painel…</section></main></AccountLayout>;
  if (error) return <AccountLayout><main className="account-main"><section className="account-state account-state--error" role="alert"><h1>Painel indisponível</h1><p>{error}</p></section></main></AccountLayout>;

  const artist = data.tipoUsuario === 'ARTISTA';
  return (
    <AccountLayout>
      <main className="account-main">
        <header className="account-heading">
          <div><p className="account-eyebrow">{artist ? 'Painel do artista' : 'Painel do contratante'}</p><h1>{artist ? 'Suas oportunidades' : 'Sua produção'}</h1><p>{artist ? 'Descubra vagas alinhadas ao seu perfil profissional.' : 'Acompanhe candidaturas e encontre pessoas para seus projetos.'}</p></div>
          <div className="account-actions">
            {artist ? <><a href="/vagas">Explorar vagas</a><a className="btn btn--primario" href="/perfil">Editar perfil</a></> : <><a href="/minhas-vagas">Minhas vagas</a><a className="btn btn--primario" href="/vagas/nova">Publicar vaga</a></>}
          </div>
        </header>
        <section className="account-hero">
          {data.avatarUrl ? <img src={data.avatarUrl} alt="" /> : null}
          <div><h2>Olá, {data.nomeExibicao}</h2><p>{artist ? 'As recomendações usam somente as áreas do seu perfil.' : 'Candidaturas e sugestões respeitam suas vagas e sua identidade autenticada.'}</p></div>
        </section>
        <section className="account-modules" aria-label="Recursos da conta">
          <NotificationPanel />
          <Availability title="Mensagens" data={data.mensagens} href="/mensagens" action="Abrir mensagens" />
        </section>
        <div className="account-panels">
          {artist ? <ArtistDashboard data={data} /> : <ContractorDashboard data={data} />}
        </div>
      </main>
    </AccountLayout>
  );
}
