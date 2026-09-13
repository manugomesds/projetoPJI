import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import BackToTopButton from '../../components/common/BackToTopButton';
import VacancyCard from '../../components/vagas/VacancyCard';
import { getVacancyPage } from '../../services/vagas/vacancyListingService';

const DEMO_VACANCIES = [
  {
    title: 'Cantora para recepção cultural',
    author: 'Vaga demonstrativa · Música',
    location: 'São José dos Campos, SP · Evento presencial',
    description: 'Voz solo para repertório brasileiro em uma recepção cultural ao ar livre.',
    image: '/assets/home/hero-vaga-jardim.png',
  },
  {
    title: 'Cantora de jazz',
    author: 'Vaga demonstrativa · Música',
    location: 'São Paulo, SP · R$ 180 por noite',
    description: 'Cantora de bar com repertório de jazz e disponibilidade para apresentações noturnas.',
    image: '/assets/home/hero-vaga-bar.png',
  },
  {
    title: 'Produção para festival independente',
    author: 'Vaga demonstrativa · Produção',
    location: 'Campinas, SP · Evento presencial',
    description: 'Apoio de produção e organização de bastidores para programação cultural.',
    image: '/assets/vaga-foto-1.png',
  },
];

const PORTFOLIOS = [
  ['artes-visuais', 'portfolio-sofhie.png', 'Sophie Petrova', 'Artes Visuais · Design', 'São Paulo, SP'],
  ['arte-integrada', 'portfolio-nina.png', 'Nina Martins', 'Arte Integrada · Video Maker', 'Belo Horizonte, MG'],
  ['danca', 'portfolio-beatriz.png', 'Beatriz Lima', 'Dança · Coreógrafa', 'Salvador, BA'],
  ['artes-cenicas', 'portfolio-rafael-julia.png', 'Rafael e Júlia', 'Artes Cênicas · Performance', 'Curitiba, PR'],
  ['musica', 'portfolio-abel.png', 'Abel', 'Música · Cantor', 'Rio de Janeiro, RJ'],
];

const TOP_ARTISTS = [
  ['top-semana-julia.jpg', 'Julia Costa', 'Dança', 'São Paulo, SP', '96'],
  ['top-semana-ebony.png', 'Ebony', 'Artes visuais', 'Rio de Janeiro, RJ', '128'],
  ['top-semana-tyla.jpg', 'Tyla', 'Dança', 'Minas Gerais', '112'],
];

function PendingLink({ className = '', title, children, ariaLabel }) {
  return (
    <span
      className={`${className} rota-pendente`.trim()}
      role="link"
      aria-disabled="true"
      aria-label={ariaLabel}
      tabIndex={0}
      title={title}
    >
      {children}
    </span>
  );
}

function DemoVacancyCard({ vacancy, featured }) {
  return (
    <li className={`vaga-mini${featured ? ' vaga-mini--destaque' : ''}`}>
      <img className="vaga-mini__foto" src={vacancy.image} alt="" />
      <div className="vaga-mini__corpo">
        <h2 className="vaga-mini__titulo">{vacancy.title}</h2>
        <p className="vaga-mini__autor">{vacancy.author}</p>
        <p className="vaga-mini__local">{vacancy.location}</p>
        <p className="vaga-mini__descricao"><strong>Descrição</strong> {vacancy.description}</p>
        <PendingLink
          className="btn-palco btn-palco--amarelo vaga-mini__cta"
          title="Vaga demonstrativa sem identificador real do RF03"
        >
          Ver vaga
        </PendingLink>
      </div>
    </li>
  );
}

function VacancyCarousel() {
  const [state, setState] = useState({ status: 'loading', vacancies: [] });
  const [index, setIndex] = useState(0);
  const trackRef = useRef(null);
  const requestIdRef = useRef(0);

  const loadVacancies = useCallback(async () => {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setState({ status: 'loading', vacancies: [] });
    try {
      const response = await getVacancyPage({ size: 8 });
      if (requestId !== requestIdRef.current) return;
      const vacancies = (Array.isArray(response.content) ? response.content : [])
        .filter((vacancy) => vacancy?.id !== null && vacancy?.id !== undefined)
        .slice(0, 8);
      setState({ status: vacancies.length ? 'success' : 'empty', vacancies });
    } catch (error) {
      if (requestId !== requestIdRef.current) return;
      setState({
        status: 'error',
        vacancies: [],
        message: error?.message || 'Não foi possível carregar as vagas agora.',
      });
    }
  }, []);

  useEffect(() => {
    loadVacancies();
    return () => { requestIdRef.current += 1; };
  }, [loadVacancies]);

  const displayedItems = state.status === 'success' ? state.vacancies : DEMO_VACANCIES;

  function move(delta) {
    const next = Math.max(0, Math.min(index + delta, displayedItems.length - 1));
    setIndex(next);
    const track = trackRef.current;
    const item = track?.children[next];
    if (!track || !item) return;
    const left = item.offsetLeft + item.offsetWidth / 2 - track.clientWidth / 2;
    if (typeof track.scrollTo === 'function') track.scrollTo({ left: Math.max(0, left), behavior: 'smooth' });
    else track.scrollLeft = Math.max(0, left);
  }

  return (
    <div className="vitrine" data-carrossel>
      <button className="seta seta--anterior" type="button" onClick={() => move(-1)} disabled={index === 0}>
        <span className="sr-only">Vaga anterior</span><span aria-hidden="true">‹</span>
      </button>
      <div className="home-vagas-status" aria-live="polite">
        {state.status === 'loading' ? <p role="status">Carregando oportunidades…</p> : null}
        {state.status === 'error' ? (
          <div role="alert">
            <p>{state.message}</p>
            <button type="button" onClick={loadVacancies}>Tentar novamente</button>
          </div>
        ) : null}
        {state.status === 'empty' ? <p>Nenhuma vaga aberta disponível no momento.</p> : null}
      </div>
      <ul
        className="vitrine__trilha"
        ref={trackRef}
        aria-label={state.status === 'success' ? 'Vagas reais em destaque' : 'Vagas demonstrativas sem destino ativo'}
      >
        {state.status === 'success'
          ? state.vacancies.map((vacancy, itemIndex) => (
            <VacancyCard
              key={vacancy.id}
              vacancy={vacancy}
              variant="home"
              index={itemIndex}
              featured={itemIndex === 1}
            />
          ))
          : DEMO_VACANCIES.map((vacancy, itemIndex) => (
            <DemoVacancyCard key={vacancy.title} vacancy={vacancy} featured={itemIndex === 1} />
          ))}
      </ul>
      <button
        className="seta seta--proxima"
        type="button"
        onClick={() => move(1)}
        disabled={index >= displayedItems.length - 1}
      >
        <span className="sr-only">Próxima vaga</span><span aria-hidden="true">›</span>
      </button>
    </div>
  );
}

function HomeHeader() {
  const [exploreOpen, setExploreOpen] = useState(false);
  const exploreRef = useRef(null);

  useEffect(() => {
    function closeOnOutsideClick(event) {
      if (!exploreRef.current?.contains(event.target)) setExploreOpen(false);
    }
    function closeOnEscape(event) {
      if (event.key === 'Escape') setExploreOpen(false);
    }
    document.addEventListener('click', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('click', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, []);

  return (
    <header className="topo">
      <nav className="navbar navbar--home" aria-label="Navegação principal">
        <a className="marca" href="/" aria-current="page">
          <img className="navbar__logo" src="/assets/home/logo-palco-branco.png" alt="" />
          <span className="marca__nome titulo-display">Palco</span>
        </a>
        <ul className="navbar__menu">
          <li className="explorar" ref={exploreRef}>
            <button
              className="navbar__link navbar__link--ativo explorar__gatilho"
              type="button"
              aria-expanded={exploreOpen}
              aria-controls="menu-explorar"
              onClick={(event) => { event.stopPropagation(); setExploreOpen((open) => !open); }}
            >
              Explorar
            </button>
            <div className="explorar__menu" id="menu-explorar" hidden={!exploreOpen}>
              <a className="explorar__item explorar__item--ativo" href="/vagas">Vagas</a>
              <PendingLink className="explorar__item" title="Busca de artistas ainda não disponível">Artistas</PendingLink>
              <PendingLink className="explorar__item" title="Catálogo de contratantes ainda não disponível">Contratantes</PendingLink>
              <PendingLink className="explorar__item" title="Galeria Virtual (RF20) ainda não disponível">Galeria Virtual</PendingLink>
              <PendingLink className="explorar__item" title="Top da semana (RF28) ainda não disponível">Top da semana</PendingLink>
            </div>
          </li>
          <li><PendingLink className="navbar__link" title="Comunidade (RF19) ainda não disponível">Comunidade</PendingLink></li>
          <li><a className="navbar__link" href="#sobre">Empresa</a></li>
        </ul>
        <div className="navbar__acoes">
          <a className="navbar__link" href="/login">Login</a>
          <a className="navbar__link navbar__link--destaque" href="/cadastro">Cadastrar</a>
        </div>
      </nav>
      <div className="hero">
        <div className="hero__texto">
          <h1 className="hero__titulo titulo-display">
            Palco onde<br />artistas encontram<br /><span className="hero__titulo-forte">oportunidades</span>
          </h1>
          <p className="hero__marca">Atividade - Fim</p>
          <div className="hero__acoes">
            <a className="btn-palco btn-palco--amarelo" href="/vagas">Conheça Vagas</a>
            <PendingLink className="btn-palco btn-palco--texto" title="Busca de artistas ainda não disponível">
              <span aria-hidden="true">←</span> Conheça Artistas
            </PendingLink>
          </div>
        </div>
        <div className="hero__vitrine">
          <img className="hero__luz" src="/assets/home/hero-luz.svg" alt="" />
          <VacancyCarousel />
        </div>
      </div>
    </header>
  );
}

function PresentationSection() {
  return (
    <section className="apresentacao" aria-labelledby="titulo-apresentacao">
      <div className="apresentacao__interno">
        <div className="colagem">
          <img className="colagem__foto colagem__foto--discos" src="/assets/home/apresentacao-discos.png" alt="" />
          <figure className="colagem__foto colagem__foto--principal">
            <img src="/assets/home/apresentacao-tyla.png" alt="" />
            <figcaption className="colagem__credito"><span className="colagem__nome">Tyla</span><span className="colagem__arroba">@tylaGomes</span></figcaption>
          </figure>
          <img className="colagem__foto colagem__foto--silhueta" src="/assets/home/apresentacao-silhueta.png" alt="" />
        </div>
        <div className="publicos">
          <h2 className="sr-only" id="titulo-apresentacao">O lugar em que artistas, contratantes e comunidade se encontram</h2>
          <p className="publicos__intro" aria-hidden="true">O lugar em que</p>
          <ul className="publicos__lista" aria-hidden="true">
            <li className="publicos__item publicos__item--fraco">Artistas</li>
            <li className="publicos__item publicos__item--forte">Contratantes</li>
            <li className="publicos__item publicos__item--fraco">Comunidade</li>
          </ul>
          <p className="publicos__fim" aria-hidden="true">se encontram</p>
        </div>
      </div>
    </section>
  );
}

function VacancyFeatureSection() {
  const navigate = useNavigate();

  function search(event) {
    event.preventDefault();
    const title = new FormData(event.currentTarget).get('titulo')?.toString().trim();
    navigate(title ? `/vagas?titulo=${encodeURIComponent(title)}` : '/vagas');
  }

  return (
    <section className="vagas" aria-labelledby="titulo-vagas">
      <img className="vagas__fundo" src="/assets/home/faixa-show.png" alt="" />
      <div className="vagas__interno">
        <div className="vagas__texto">
          <h2 className="vagas__titulo titulo-display" id="titulo-vagas">Encontre a sua<br /><span className="vagas__titulo-forte">vaga</span> ideal</h2>
          <p className="vagas__lead">Separada por área artística e subcategoria, <strong>palco</strong> oferece sua melhor oportunidade no mercado artístico.</p>
          <form className="busca" role="search" onSubmit={search}>
            <label className="sr-only" htmlFor="busca-vaga">Buscar vagas</label>
            <input className="busca__input" type="search" id="busca-vaga" name="titulo" placeholder="Busque por título da vaga" />
            <button className="busca__botao" type="submit">Buscar</button>
          </form>
        </div>
        <article className="vaga-detalhe">
          <div className="vaga-detalhe__topo">
            <div className="vaga-detalhe__cartaz"><img src="/assets/home/vaga-paisagem-sonora.jpg" alt="Cartaz do Festival Paisagem Sonora 2025" /></div>
            <div className="vaga-detalhe__cabecalho">
              <h3 className="vaga-detalhe__cargo">Produtor(a) de Eventos</h3>
              <p className="vaga-detalhe__meta">Festival Sonora · oportunidade demonstrativa</p>
              <ul className="chips"><li className="chip">Produção</li><li className="chip">Evento</li><li className="chip">Regional</li><li className="chip">Presencial</li></ul>
            </div>
          </div>
          <h4 className="vaga-detalhe__rotulo">Descrição da oportunidade</h4>
          <p className="vaga-detalhe__texto">Exemplo visual da landing. Consulte a busca para acessar vagas reais e seus detalhes.</p>
          <div className="vaga-detalhe__galeria"><img src="/assets/home/vaga-detalhe-1.png" alt="" /><img src="/assets/home/vaga-detalhe-2.png" alt="" /><img src="/assets/home/vaga-detalhe-3.png" alt="" /></div>
        </article>
      </div>
    </section>
  );
}

function PortfolioSection() {
  const [category, setCategory] = useState('todos');
  const visible = PORTFOLIOS.filter(([itemCategory]) => category === 'todos' || itemCategory === category);
  const filters = [
    ['todos', 'Todos'], ['musica', 'Música'], ['artes-visuais', 'Artes Visuais'],
    ['artes-cenicas', 'Artes Cênicas'], ['danca', 'Dança'], ['arte-integrada', 'Arte Integrada'],
  ];

  return (
    <section className="portfolios" aria-labelledby="titulo-portfolios">
      <img className="portfolios__sombra" src="/assets/home/portfolios-sombra-base.svg" alt="" />
      <div className="portfolios__interno">
        <p className="eyebrow">Inspire-se. Conecte-se. Transforme.</p>
        <h2 className="portfolios__titulo titulo-display" id="titulo-portfolios">Acesse <span className="portfolios__titulo-forte">portfólio</span><br />dos nossos artistas.</h2>
        <p className="portfolios__lead">Explore trabalhos demonstrativos de artistas independentes.</p>
        <ul className="filtros-arte" aria-label="Filtrar portfólios por categoria">
          {filters.map(([value, label]) => (
            <li key={value}><button className={`chip-filtro${category === value ? ' chip-filtro--ativo' : ''}`} type="button" aria-pressed={category === value} onClick={() => setCategory(value)}>{label}</button></li>
          ))}
        </ul>
        <p className="sr-only" aria-live="polite">{visible.length} portfólios demonstrativos exibidos.</p>
        <ul className="portfolio-cards" id="lista-portfolios">
          {visible.map(([itemCategory, image, name, area, location]) => (
            <li className="portfolio-card" data-categoria={itemCategory} key={name}>
              <img className="portfolio-card__foto" src={`/assets/home/${image}`} alt={`Portfólio de ${name}`} />
              <div className="portfolio-card__corpo"><h3 className="portfolio-card__nome">{name}</h3><p className="portfolio-card__area">{area}</p><p className="portfolio-card__local">{location}</p></div>
            </li>
          ))}
        </ul>
        <PendingLink className="btn-palco btn-palco--amarelo btn-palco--largo portfolios__cta" title="Galeria Virtual (RF20) ainda não disponível">Explorar Portfólios →</PendingLink>
      </div>
    </section>
  );
}

function AdditionalHomeSections() {
  return (
    <>
      <section className="comunidades" aria-labelledby="titulo-comunidades">
        <img className="comunidades__fundo" src="/assets/home/faixa-show.png" alt="Banda em apresentação" />
        <div className="comunidades__interno"><p className="comunidades__eyebrow">Comunidades</p><h2 className="comunidades__titulo titulo-display" id="titulo-comunidades">Nossas<br />comunidades</h2><PendingLink className="btn-palco btn-palco--amarelo comunidades__cta" title="Comunidade (RF19) ainda não disponível">Saiba mais</PendingLink></div>
      </section>
      <section className="top-semana" aria-labelledby="titulo-top-semana">
        <div className="top-semana__interno">
          <div className="palco-artistas"><ul className="palco-artistas__trilha" aria-label="Artistas demonstrativos do Top da Semana">
            {TOP_ARTISTS.map(([image, name, area, location, interactions], itemIndex) => (
              <li className={`artista-card${itemIndex === 1 ? ' artista-card--destaque' : ''}`} key={name}>
                <img className="artista-card__foto" src={`/assets/home/${image}`} alt={`Retrato de ${name}`} />
                <div className="artista-card__corpo"><h3 className="artista-card__nome">{name}</h3><p className="artista-card__meta"><span className="artista-card__area">{area}</span> · {location}</p><p className="artista-card__interacoes">{interactions} interações esta semana</p></div>
                <PendingLink className="artista-card__ver" title="Perfil demonstrativo sem identificador real do RF10" ariaLabel={`Perfil demonstrativo de ${name} indisponível`}>Ver perfil</PendingLink>
              </li>
            ))}
          </ul></div>
          <div className="top-semana__texto"><h2 className="top-semana__titulo titulo-display" id="titulo-top-semana">Top da<br /><span className="top-semana__titulo-forte">semana</span></h2><p className="top-semana__lead">Uma vitrine demonstrativa para descobrir novos perfis.</p><PendingLink className="btn-palco btn-palco--amarelo" title="Top da semana (RF28) ainda não disponível">Ver artistas em destaque →</PendingLink></div>
        </div>
      </section>
      <section className="trajetorias" aria-labelledby="titulo-trajetorias">
        <div className="trajetorias__interno"><div className="trajetorias__texto"><p className="eyebrow">Histórias reais</p><h2 className="trajetorias__titulo titulo-display" id="titulo-trajetorias">Trajetórias<br />dos artistas</h2><p className="trajetorias__lead">Do perfil organizado à oportunidade conquistada: visibilidade e conexão profissional.</p></div>
          <ol className="passos"><li className="passo"><span className="passo__numero">01</span><div><h3 className="passo__titulo">Perfil completo</h3><p className="passo__texto">O artista organiza bio, localização, funções e portfólio.</p></div></li><li className="passo"><span className="passo__numero">02</span><div><h3 className="passo__titulo">Ganha visibilidade</h3><p className="passo__texto">O perfil aparece em buscas e recomendações.</p></div></li><li className="passo"><span className="passo__numero">03</span><div><h3 className="passo__titulo">Novas oportunidades</h3><p className="passo__texto">Vagas e projetos começam a fazer parte da jornada.</p></div></li></ol>
        </div>
      </section>
      <section className="sobre" aria-labelledby="titulo-sobre" id="sobre">
        <div className="sobre__interno"><h2 className="sobre__titulo titulo-display" id="titulo-sobre">Sobre nós</h2><p className="sobre__texto">Um projeto que nasceu no IFSP de São Miguel Paulista para valorizar artistas independentes locais e criar conexões: <strong>palco</strong>.</p></div>
        <div className="equipe"><ul className="equipe__trilha">{[['Giovana', 'Scrum Master'], ['Larissa', 'Product Owner'], ['Rayza', 'Designer'], ['Mariana', 'Designer']].map(([name, role]) => <li className="equipe__pessoa" key={name}><img src="/assets/home/equipe-placeholder.png" alt="" /><p className="equipe__nome">{name}</p><p className="equipe__papel">{role}</p></li>)}</ul></div>
      </section>
      <section className="saq" aria-labelledby="titulo-saq" id="saq"><div className="saq__interno"><div className="saq__texto"><h2 className="saq__titulo titulo-display" id="titulo-saq">Perguntas<br />frequentes</h2><a className="btn-palco btn-palco--amarelo btn-palco--pequeno" href="mailto:suporte@palco.com.br">Enviar</a></div><div className="saq__lista"><details className="pergunta"><summary className="pergunta__titulo">1. Quais são as áreas artísticas implementadas no site?</summary><p className="pergunta__resposta">Música, artes visuais, artes cênicas, dança e arte integrada.</p></details><details className="pergunta"><summary className="pergunta__titulo">2. O site aceita o cadastro de menores de 18?</summary><p className="pergunta__resposta">Sim, conforme as regras de consentimento aplicáveis.</p></details><details className="pergunta"><summary className="pergunta__titulo">3. Como faço para denunciar uma vaga ou perfil?</summary><p className="pergunta__resposta">A funcionalidade depende do fluxo de denúncia disponível na plataforma.</p></details></div></div></section>
    </>
  );
}

function HomeFooter() {
  return (
    <footer className="rodape"><div className="rodape__interno">
      <a className="rodape__marca" href="/" aria-label="Palco — voltar ao início"><img className="rodape__logo" src="/assets/home/logo-palco-branco.png" alt="" /></a>
      <nav className="rodape__coluna" aria-labelledby="rodape-navegar"><h2 className="rodape__rotulo" id="rodape-navegar">Navegar</h2><ul><li><a href="/vagas">Vagas</a></li><li><PendingLink title="Busca de artistas ainda não disponível">Artistas</PendingLink></li><li><PendingLink title="Comunidade ainda não disponível">Comunidade</PendingLink></li><li><a href="#sobre">Sobre nós</a></li></ul></nav>
      <nav className="rodape__coluna" aria-labelledby="rodape-suporte"><h2 className="rodape__rotulo" id="rodape-suporte">Suporte</h2><ul><li><a href="#saq">SAQ</a></li><li><a href="mailto:suporte@palco.com.br">Email de suporte</a></li><li><PendingLink title="Telefone de suporte ainda não configurado">Telefone de suporte</PendingLink></li></ul></nav>
      <p className="rodape__manifesto">Conectando artistas independentes com oportunidades e conexões profissionais.</p>
    </div><div className="rodape__base"><ul className="rodape__legal"><li><PendingLink title="Política de privacidade ainda não publicada">Política de privacidade</PendingLink></li><li><PendingLink title="Termos de Uso ainda não publicados">Termos de Uso</PendingLink></li></ul></div></footer>
  );
}

export default function HomePage() {
  useEffect(() => {
    document.body.classList.add('pagina-home');
    document.title = 'Palco — onde artistas encontram oportunidades';
    return () => document.body.classList.remove('pagina-home');
  }, []);

  return (
    <>
      <HomeHeader />
      <main><PresentationSection /><VacancyFeatureSection /><PortfolioSection /><AdditionalHomeSections /></main>
      <HomeFooter />
      <BackToTopButton variant="home" />
    </>
  );
}

export { DEMO_VACANCIES, PendingLink, VacancyCarousel };
