import { useSearchParams } from 'react-router-dom';
import RegistrationPage from './RegistrationPage';
import UserTypePage from './UserTypePage';

export default function RegistrationFlow() {
  const [params, setParams] = useSearchParams();
  const type = params.get('tipoUsuario');
  if (type === 'ARTISTA' || type === 'CONTRATANTE') {
    return <RegistrationPage initialType={type} onTypeChange={(tipoUsuario) => setParams({ tipoUsuario }, { replace: true })} />;
  }
  return <UserTypePage onSelect={(tipoUsuario) => setParams({ tipoUsuario })} />;
}
