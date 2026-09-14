import LogoutButton from '../account/LogoutButton';
import TalentLink from '../account/TalentLink';

export default function ContractorVacancyLayout({ children }) {
  return (
    <div className="pagina-app management-shell">
      <header>
        <nav className="app-navbar management-navbar" aria-label="Navegação da gestão de vagas">
          <a href="/dashboard" aria-label="Palco — painel do contratante">
            <img className="navbar__logo" src="/assets/logo-palco.png" alt="Palco" />
          </a>
          <ul className="app-navbar__menu">
            <li><a className="navbar__link navbar__link--destaque" href="/minhas-vagas">Minhas vagas</a></li>
            <li><a className="navbar__link" href="/vagas/nova">Publicar vaga</a></li>
            <li><TalentLink className="navbar__link" /></li>
            <li><a className="navbar__link" href="/notificacoes.html">Notificações</a></li>
          </ul>
          <div className="app-navbar__acoes management-navbar__actions">
            <a className="navbar__link" href="/dashboard">Painel</a>
            <a className="navbar__link" href="/perfil">Perfil</a>
            <LogoutButton className="management-logout" />
          </div>
        </nav>
      </header>
      {children}
    </div>
  );
}
