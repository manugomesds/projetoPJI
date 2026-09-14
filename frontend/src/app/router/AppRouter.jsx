import { BrowserRouter, Route, Routes } from 'react-router-dom';
import NotFound from '../../components/common/NotFound';
import ForgotPasswordPage from '../../pages/auth/ForgotPasswordPage';
import LoginPage from '../../pages/auth/LoginPage';
import RegistrationPage from '../../pages/auth/RegistrationPage';
import ResetPasswordPage from '../../pages/auth/ResetPasswordPage';
import HomePage from '../../pages/home/HomePage';
import PublicProfilePage from '../../pages/perfis/PublicProfilePage';
import ContractorOnly from '../../components/vagas/ContractorOnly';
import MyVacanciesPage from '../../pages/vagas/MyVacanciesPage';
import VagaDetailPage from '../../pages/vagas/VagaDetailPage';
import VacancySearchPage from '../../pages/vagas/VacancySearchPage';
import VacancyCreatePage from '../../pages/vagas/VacancyCreatePage';
import VacancyEditPage from '../../pages/vagas/VacancyEditPage';
import VacancyManagePage from '../../pages/vagas/VacancyManagePage';
import AuthenticatedOnly from '../../components/account/AuthenticatedOnly';
import DashboardPage from '../../pages/account/DashboardPage';
import ProfilePage from '../../pages/account/ProfilePage';
import MessagesPage from '../../pages/chat/MessagesPage';
import TalentBankPage from '../../pages/talentos/TalentBankPage';

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/cadastro" element={<RegistrationPage />} />
      <Route path="/recuperar-senha" element={<ForgotPasswordPage />} />
      <Route path="/redefinir-senha" element={<ResetPasswordPage />} />
      <Route path="/dashboard" element={<AuthenticatedOnly><DashboardPage /></AuthenticatedOnly>} />
      <Route path="/perfil" element={<AuthenticatedOnly><ProfilePage /></AuthenticatedOnly>} />
      <Route path="/mensagens" element={<AuthenticatedOnly><MessagesPage /></AuthenticatedOnly>} />
      <Route path="/vagas" element={<VacancySearchPage />} />
      <Route path="/talentos" element={<ContractorOnly description="Seu perfil não tem permissão para consultar o Banco de Talentos."><TalentBankPage /></ContractorOnly>} />
      <Route path="/minhas-vagas" element={<ContractorOnly><MyVacanciesPage /></ContractorOnly>} />
      <Route path="/vagas/nova" element={<ContractorOnly><VacancyCreatePage /></ContractorOnly>} />
      <Route path="/vagas/:id/gerenciar" element={<ContractorOnly><VacancyManagePage /></ContractorOnly>} />
      <Route path="/vagas/:id/editar" element={<ContractorOnly><VacancyEditPage /></ContractorOnly>} />
      <Route path="/vagas/:id" element={<VagaDetailPage />} />
      <Route path="/perfis/:tipo/:id" element={<PublicProfilePage />} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}

export default function AppRouter() {
  return (
    <BrowserRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
      <AppRoutes />
    </BrowserRouter>
  );
}
