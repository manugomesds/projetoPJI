import { render, screen } from '@testing-library/react';
import { SESSION_STORAGE_KEY } from '../../auth/sessionService';
import TalentLink from './TalentLink';

beforeEach(()=>{sessionStorage.clear();localStorage.clear();});
test.each(['ARTISTA','ADMIN',null])('Artistas indisponível para %s', (tipoUsuario)=>{
  if(tipoUsuario) sessionStorage.setItem(SESSION_STORAGE_KEY,JSON.stringify({token:'jwt',tipoUsuario}));
  render(<TalentLink />);
  expect(screen.getByRole('link',{name:'Artistas'})).toHaveAttribute('aria-disabled','true');
  expect(screen.getByRole('link',{name:'Artistas'})).not.toHaveAttribute('href');
});
test('Artistas habilitado para CONTRATANTE',()=>{
  sessionStorage.setItem(SESSION_STORAGE_KEY,JSON.stringify({token:'jwt',tipoUsuario:'CONTRATANTE',statusConta:'ATIVA'}));
  render(<TalentLink />);expect(screen.getByRole('link',{name:'Artistas'})).toHaveAttribute('href','/talentos');
});
