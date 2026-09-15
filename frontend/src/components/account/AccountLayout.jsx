import LogoutButton from './LogoutButton';
import TalentLink from './TalentLink';

export default function AccountLayout({ children }) {
  return (
    <div className="pagina-app account-shell">
      <header>
        <nav className="app-navbar account-navbar" aria-label="Navegação da conta">
          <a href="/dashboard" aria-label="Palco — painel principal">
            <img className="navbar__logo" src="/assets/logo-palco.png" alt="Palco" />
          </a>
          <ul className="app-navbar__menu">
            <li><a className="navbar__link" href="/dashboard">Dashboard</a></li>
            <li><a className="navbar__link" href="/perfil">Meu perfil</a></li>
            <li><a className="navbar__link" href="/salvos">Meus Salvos</a></li>
            <li><TalentLink className="navbar__link" /></li>
            <li><a className="navbar__link" href="/mensagens">Mensagens</a></li>
            <li><a className="navbar__link" href="/minhas-candidaturas.html">Candidaturas</a></li>
            <li><a className="navbar__link" href="/notificacoes.html">Notificações</a></li>
          </ul>
          <div className="app-navbar__acoes">
            <LogoutButton />
          </div>
        </nav>
      </header>
      {children}
    </div>
  );
}
