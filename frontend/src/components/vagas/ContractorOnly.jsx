import { Navigate, useLocation } from 'react-router-dom';
import sessionService from '../../auth/sessionService';

export default function ContractorOnly({ children, description = 'Seu perfil não pode publicar ou gerenciar vagas.' }) {
  const location = useLocation();
  const session = sessionService.getSession();

  if (!session?.token) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (session.tipoUsuario !== 'CONTRATANTE') {
    return (
      <main className="vacancy-management vacancy-management--center">
        <section className="management-state management-state--error" role="alert">
          <p className="management-eyebrow">Acesso protegido</p>
          <h1>Área exclusiva para contratantes</h1>
          <p>{description}</p>
          <a className="btn btn--primario" href="/vagas">Explorar vagas</a>
        </section>
      </main>
    );
  }

  return children;
}
