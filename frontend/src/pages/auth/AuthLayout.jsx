import { PublicNavigation } from './UserTypePage';

export default function AuthLayout({ children, currentPage }) {
  return <div className={`auth-page auth-page--${currentPage}`} style={{ '--auth-background': 'url("/assets/tipo-usuario/fundo.png")' }}>
    <PublicNavigation currentPage={currentPage} />
    {children}
  </div>;
}
