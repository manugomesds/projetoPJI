import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppRoutes } from './AppRouter';

jest.mock('../../pages/home/HomePage', () => function HomePageMock() {
  return <h1>Home Palco React</h1>;
});

jest.mock('../../pages/vagas/VacancySearchPage', () => function VacancySearchPageMock() {
  return <h1>Busca de vagas React</h1>;
});

jest.mock('../../pages/auth/ForgotPasswordPage', () => function ForgotPasswordPageMock() {
  return <h1>Recuperar senha React</h1>;
});

jest.mock('../../pages/auth/ResetPasswordPage', () => function ResetPasswordPageMock() {
  return <h1>Redefinir senha React</h1>;
});

jest.mock('../../pages/auth/LoginPage', () => function LoginPageMock() {
  return <h1>Login React</h1>;
});

jest.mock('../../pages/auth/RegistrationPage', () => function RegistrationPageMock() {
  return <h1>Cadastro React</h1>;
});

jest.mock('../../components/vagas/ContractorOnly', () => function ContractorOnlyMock({ children }) {
  return children;
});

jest.mock('../../components/account/AuthenticatedOnly', () => function AuthenticatedOnlyMock({ children }) {
  return children;
});

jest.mock('../../pages/account/DashboardPage', () => function DashboardPageMock() {
  return <h1>Dashboard React</h1>;
});

jest.mock('../../pages/account/ProfilePage', () => function ProfilePageMock() {
  return <h1>Perfil privado React</h1>;
});

jest.mock('../../pages/chat/MessagesPage', () => function MessagesPageMock() {
  return <h1>Mensagens React</h1>;
});

jest.mock('../../pages/vagas/MyVacanciesPage', () => function MyVacanciesPageMock() {
  return <h1>Minhas vagas React</h1>;
});

jest.mock('../../pages/vagas/VacancyCreatePage', () => function VacancyCreatePageMock() {
  return <h1>Publicar vaga React</h1>;
});

jest.mock('../../pages/vagas/VacancyManagePage', () => function VacancyManagePageMock() {
  const { useParams } = jest.requireActual('react-router-dom');
  return <h1>Gerenciar vaga {useParams().id}</h1>;
});

jest.mock('../../pages/vagas/VacancyEditPage', () => function VacancyEditPageMock() {
  const { useParams } = jest.requireActual('react-router-dom');
  return <h1>Editar vaga {useParams().id}</h1>;
});

jest.mock('../../pages/vagas/VagaDetailPage', () => {
  const { useParams } = jest.requireActual('react-router-dom');

  return function VagaDetailPageMock() {
    const { id } = useParams();
    return <h1>Detalhe da vaga {id}</h1>;
  };
});

jest.mock('../../pages/perfis/PublicProfilePage', () => {
  const { useParams } = jest.requireActual('react-router-dom');

  return function PublicProfilePageMock() {
    const { tipo, id } = useParams();
    return <h1>Perfil público {tipo} {id}</h1>;
  };
});

test('renderiza a Home React na rota inicial', () => {
  render(
    <MemoryRouter
      initialEntries={['/']}
      future={{ v7_relativeSplatPath: true, v7_startTransition: true }}
    >
      <AppRoutes />
    </MemoryRouter>
  );

  expect(screen.getByRole('heading', { name: 'Home Palco React' })).toBeInTheDocument();
});

test('renderiza a busca pública na rota /vagas', () => {
  render(
    <MemoryRouter
      initialEntries={['/vagas?cidade=Recife']}
      future={{ v7_relativeSplatPath: true, v7_startTransition: true }}
    >
      <AppRoutes />
    </MemoryRouter>
  );

  expect(screen.getByRole('heading', { name: 'Busca de vagas React' })).toBeInTheDocument();
});

test('renderiza o fallback para uma rota React inexistente', () => {
  render(
    <MemoryRouter
      initialEntries={['/rota-inexistente']}
      future={{ v7_relativeSplatPath: true, v7_startTransition: true }}
    >
      <AppRoutes />
    </MemoryRouter>
  );

  expect(screen.getByRole('heading', { name: /página não encontrada/i })).toBeInTheDocument();
});

test('monta a página de detalhe e entrega o parâmetro da rota', () => {
  render(
    <MemoryRouter
      initialEntries={['/vagas/77']}
      future={{ v7_relativeSplatPath: true, v7_startTransition: true }}
    >
      <AppRoutes />
    </MemoryRouter>
  );

  expect(screen.getByRole('heading', { name: 'Detalhe da vaga 77' })).toBeInTheDocument();
});

test('monta o perfil público e entrega tipo e ID da rota', () => {
  render(
    <MemoryRouter
      initialEntries={['/perfis/ARTISTA/91']}
      future={{ v7_relativeSplatPath: true, v7_startTransition: true }}
    >
      <AppRoutes />
    </MemoryRouter>
  );

  expect(screen.getByRole('heading', { name: 'Perfil público ARTISTA 91' })).toBeInTheDocument();
});

test.each([
  ['/login', 'Login React'],
  ['/cadastro', 'DEFINA O SEU TIPO DE USUÁRIO'],
  ['/cadastro?tipoUsuario=ARTISTA', 'Cadastro React'],
  ['/cadastro?tipoUsuario=CONTRATANTE', 'Cadastro React'],
  ['/recuperar-senha', 'Recuperar senha React'],
  ['/redefinir-senha', 'Redefinir senha React'],
])('monta a rota pública do RF09 %s', (path, heading) => {
  render(
    <MemoryRouter
      initialEntries={[path]}
      future={{ v7_relativeSplatPath: true, v7_startTransition: true }}
    >
      <AppRoutes />
    </MemoryRouter>
  );

  expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument();
});

test.each([
  ['/minhas-vagas', 'Minhas vagas React'],
  ['/vagas/nova', 'Publicar vaga React'],
  ['/vagas/31/gerenciar', 'Gerenciar vaga 31'],
  ['/vagas/31/editar', 'Editar vaga 31'],
])('monta a rota protegida de gestão %s', (path, heading) => {
  render(<MemoryRouter initialEntries={[path]} future={{ v7_relativeSplatPath: true, v7_startTransition: true }}><AppRoutes /></MemoryRouter>);
  expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument();
});

test.each([
  ['/dashboard', 'Dashboard React'],
  ['/perfil', 'Perfil privado React'],
  ['/mensagens', 'Mensagens React'],
])('monta a rota protegida da conta %s', (path, heading) => {
  render(<MemoryRouter initialEntries={[path]} future={{ v7_relativeSplatPath: true, v7_startTransition: true }}><AppRoutes /></MemoryRouter>);
  expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument();
});
