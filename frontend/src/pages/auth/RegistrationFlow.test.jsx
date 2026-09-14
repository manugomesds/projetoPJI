import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import RegistrationFlow from './RegistrationFlow';
import { getRegistrationAreas } from '../../services/auth/areaService';
jest.mock('../../services/auth/areaService');
function Location() { const l = useLocation(); return <output data-testid="url">{l.pathname}{l.search}</output>; }
function mount(url = '/cadastro') {
  return render(<MemoryRouter initialEntries={[url]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><RegistrationFlow /><Location /></MemoryRouter>);
}
beforeEach(() => { sessionStorage.clear(); localStorage.clear(); getRegistrationAreas.mockReturnValue(new Promise(() => {})); });
test.each([['Sou Artista', 'ARTISTA'], ['Sou contratante', 'CONTRATANTE']])('seleção %s continua no cadastro preservando tipo na URL', (button, type) => {
  mount(); expect(screen.getByText('Passo 1 de 3')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: button }));
  expect(screen.getByLabelText('Tipo de usuário')).toHaveValue(type);
  expect(screen.getByTestId('url')).toHaveTextContent('/cadastro?tipoUsuario=' + type);
  fireEvent.click(screen.getByRole('link', { name: /Escolher outro tipo/ }));
  expect(screen.getByText('Passo 1 de 3')).toBeInTheDocument();
});
test.each(['ARTISTA', 'CONTRATANTE'])('abrir URL novamente preserva %s', type => {
  mount('/cadastro?tipoUsuario=' + type);
  expect(screen.getByLabelText('Tipo de usuário')).toHaveValue(type);
});
test('tipo desconhecido volta à escolha e não cria privilégio', () => {
  mount('/cadastro?tipoUsuario=ADMIN'); expect(screen.getByText('Passo 1 de 3')).toBeInTheDocument();
});
test('troca de tipo no formulário atualiza URL e preserva dados pessoais', () => {
  mount('/cadastro?tipoUsuario=ARTISTA');
  fireEvent.change(screen.getByLabelText('Nome completo'), { target: { value: 'Lia' } });
  fireEvent.change(screen.getByLabelText('Tipo de usuário'), { target: { value: 'CONTRATANTE' } });
  expect(screen.getByTestId('url')).toHaveTextContent('tipoUsuario=CONTRATANTE');
  expect(screen.getByLabelText('Nome completo')).toHaveValue('Lia');
});
test('menu móvel abre, fecha por Escape e devolve foco', () => {
  mount(); const menu = screen.getByRole('button', { name: 'Menu', exact: true });
  fireEvent.click(menu); expect(menu).toHaveAttribute('aria-expanded', 'true');
  fireEvent.keyDown(menu, { key: 'Escape' }); expect(menu).toHaveAttribute('aria-expanded', 'false'); expect(menu).toHaveFocus();
});
