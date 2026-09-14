import { useEffect, useRef, useState } from 'react';
import './UserTypePage.css';
import sessionService from '../../auth/sessionService';
import TalentLink from '../../components/account/TalentLink';

const assets = `${process.env.PUBLIC_URL || ''}/assets/tipo-usuario/`;
const signupUrl = '/cadastro';

export function PublicNavigation({ currentPage }) {
  const session = sessionService.getSession();
  const [menuOpen, setMenuOpen] = useState(false);
  const [connectionsOpen, setConnectionsOpen] = useState(false);
  const connections = useRef(null);
  const connectionsButton = useRef(null);
  const menuButton = useRef(null);

  useEffect(() => {
    function closeOutside(event) {
      if (!connections.current?.contains(event.target)) setConnectionsOpen(false);
    }
    document.addEventListener('pointerdown', closeOutside);
    return () => document.removeEventListener('pointerdown', closeOutside);
  }, []);

  function onEscape(event) {
    if (event.key !== 'Escape') return;
    if (connectionsOpen) {
      setConnectionsOpen(false);
      connectionsButton.current.focus();
    } else if (menuOpen) {
      setMenuOpen(false);
      menuButton.current.focus();
    }
  }

  return (
    <header className="tipo-navbar" onKeyDown={onEscape}>
      <nav aria-label="Navegação principal">
        <a className="tipo-navbar__brand" href="/" aria-label="Palco — página inicial">
          <img src={`${process.env.PUBLIC_URL || ''}/assets/home/logo-palco-branco.png`} alt="Palco" width="65" height="61" />
        </a>
        <button className="tipo-navbar__toggle" type="button" ref={menuButton}
          aria-expanded={menuOpen} aria-controls="tipo-navigation"
          onClick={() => { setMenuOpen(!menuOpen); setConnectionsOpen(false); }}>
          {menuOpen ? 'Fechar menu' : 'Menu'}
        </button>
        <div className="tipo-navbar__links" id="tipo-navigation" data-open={menuOpen}>
          <ul className="tipo-navbar__sections">
            <li><TalentLink>Artistas</TalentLink></li>
            <li><a href="/#titulo-apresentacao">Contratantes</a></li>
            <li><a href="/vagas">Vagas</a></li>
            <li className="tipo-connections" ref={connections} onBlur={event => {
              if (!event.currentTarget.contains(event.relatedTarget)) setConnectionsOpen(false);
            }}>
              <button ref={connectionsButton} type="button" className="tipo-connections__trigger"
                aria-expanded={connectionsOpen} aria-controls="tipo-connections-menu"
                onClick={() => setConnectionsOpen(!connectionsOpen)}>
                Conexões <img src={assets + 'caret.svg'} alt="" width="25" height="24" />
              </button>
              <div id="tipo-connections-menu" className="tipo-connections__menu" hidden={!connectionsOpen}>
                <a href="/#titulo-comunidades">
                  <strong>Comunidades</strong>
                  <span>Conheça a proposta de comunidades da Palco</span>
                </a>
                <a href="/#titulo-portfolios">
                  <strong>Galeria Virtual</strong>
                  <span>Conheça a proposta visual da galeria de artistas</span>
                </a>
              </div>
            </li>
            <li><a href="/#titulo-top-semana">Holofotes</a></li>
            <li><a href="/#sobre">Sobre nós</a></li>
          </ul>
          <div className="tipo-navbar__account">
            <a href={session?.token ? '/dashboard' : '/login'} aria-current={currentPage === 'login' ? 'page' : undefined}>{session?.token ? 'Minha conta' : 'Login'}</a>
            {session?.token ? <a className="tipo-navbar__signup" href="/perfil">Meu perfil</a> : <a className="tipo-navbar__signup" href={signupUrl} aria-current={currentPage === 'cadastro' ? 'page' : undefined}>Cadastro</a>}
          </div>
        </div>
      </nav>
    </header>
  );
}

function UserTypeCard({ artist, onSelect }) {
  const title = artist ? 'Artista independente' : 'Contratante';
  return (
    <article className={`tipo-card tipo-card--${artist ? 'artist' : 'contractor'}`}
      aria-labelledby={artist ? 'tipo-artist-title' : 'tipo-contractor-title'}>
      <div className="tipo-card__icon" aria-hidden="true">
        <img className="tipo-card__circle" src={assets + (artist ? 'artista-circulo.svg' : 'contratante-circulo.svg')} alt="" />
        <img className="tipo-card__glyph" src={assets + (artist ? 'artista.png' : 'contratante.png')} alt="" />
      </div>
      <h2 id={artist ? 'tipo-artist-title' : 'tipo-contractor-title'}>{title}</h2>
      <p>{artist ? 'Quero divulgar meu trabalho, me candidatar a vagas e conectar meu talento a novas oportunidades.' : 'Quero publicar vagas e contratar novos talentos.'}</p>
      <button type="button" className="tipo-card__cta" onClick={() => onSelect(artist ? 'ARTISTA' : 'CONTRATANTE')}>
        {artist ? 'Sou Artista' : 'Sou contratante'}
        <img src={assets + (artist ? 'seta-artista.svg' : 'seta-contratante.svg')} alt="" width="18" height="16" />
      </button>
    </article>
  );
}

function continueSignup(type) {
  window.location.assign(`/cadastro?tipoUsuario=${encodeURIComponent(type)}`);
}

export default function UserTypePage({ onSelect = continueSignup }) {
  useEffect(() => { document.title = 'Defina seu tipo de usuário — Palco'; }, []);
  return (
    <div className="tipo-page" style={{ "--tipo-background": `url("${assets}fundo.png")` }}>
      <a className="tipo-skip" href="#tipo-main">Pular para o conteúdo</a>
      <PublicNavigation currentPage="cadastro" />
      <main id="tipo-main" className="tipo-main" tabIndex="-1">
        <div className="tipo-content">
          <section className="tipo-intro" aria-labelledby="tipo-title">
            <p className="tipo-intro__step">Passo 1 de 3</p>
            <h1 id="tipo-title"><span>DEFINA O SEU</span>TIPO DE USUÁRIO</h1>
            <p className="tipo-intro__description">Este é o momento crucial para o cadastro do seu usuário: Em qual de nossas opções você se identifica?</p>
          </section>
          <UserTypeCard artist onSelect={onSelect} />
          <UserTypeCard onSelect={onSelect} />
        </div>
      </main>
    </div>
  );
}
