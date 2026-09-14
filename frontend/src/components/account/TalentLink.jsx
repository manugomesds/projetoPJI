import sessionService from '../../auth/sessionService';

export default function TalentLink({ className = '', children = 'Artistas' }) {
  const session = sessionService.getSession();
  if (session?.token && session.tipoUsuario === 'CONTRATANTE') {
    return <a className={className} href="/talentos">{children}</a>;
  }
  return <span className={className} role="link" aria-disabled="true" title="Banco de Talentos exclusivo para contratantes">{children}</span>;
}
