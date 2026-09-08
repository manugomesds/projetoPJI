import { useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import authService from '../../services/auth/authService';
import { isPasswordValid, PASSWORD_POLICY_MESSAGE } from '../../utils/passwordPolicy';
import AuthLayout from './AuthLayout';

export function calculateAge(dateOfBirth, today = new Date()) {
  if (!dateOfBirth) return null;
  const [year, month, day] = dateOfBirth.split('-').map(Number);
  if (!year || !month || !day) return null;
  const birthDate = new Date(year, month - 1, day);
  if (Number.isNaN(birthDate.getTime()) || birthDate.getFullYear() !== year || birthDate.getMonth() !== month - 1 || birthDate.getDate() !== day) return null;
  let age = today.getFullYear() - year;
  const birthdayThisYear = new Date(today.getFullYear(), month - 1, day);
  if (today < birthdayThisYear) age -= 1;
  return age;
}

const initialForm = {
  nome: '', dataNascimento: '', telefone: '', email: '', senha: '', confirmarSenha: '',
  tipoUsuario: 'ARTISTA', tipoPerfilContratante: '', nomeResponsavel: '',
  telefoneResponsavel: '', emailResponsavel: '', termos: false,
};

export default function RegistrationPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState(initialForm);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const submittingRef = useRef(false);
  const age = useMemo(() => calculateAge(form.dataNascimento), [form.dataNascimento]);
  const needsGuardian = age !== null && age >= 14 && age < 18;

  function updateField(event) {
    const { name, value, checked, type } = event.target;
    setForm((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value }));
  }

  function validate() {
    if (!form.nome.trim() || !form.dataNascimento || !form.telefone.trim() || !form.email.trim() || !form.senha || !form.tipoUsuario) return 'Preencha todos os campos obrigatórios.';
    if (age === null) return 'Informe uma data de nascimento válida.';
    if (age < 0) return 'A data de nascimento não pode estar no futuro.';
    if (age < 14) return 'A idade mínima para cadastro é 14 anos.';
    if (!isPasswordValid(form.senha)) return PASSWORD_POLICY_MESSAGE;
    if (form.senha !== form.confirmarSenha) return 'As senhas não conferem.';
    if (needsGuardian && (!form.nomeResponsavel.trim() || !form.telefoneResponsavel.trim() || !form.emailResponsavel.trim())) return 'Informe nome, telefone e e-mail do responsável legal.';
    if (!form.termos) return 'É preciso aceitar os Termos de Uso para continuar.';
    return '';
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (submittingRef.current) return;
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    submittingRef.current = true;
    setLoading(true);
    setError('');
    try {
      await authService.cadastrar({
        ...form,
        nomeResponsavel: needsGuardian ? form.nomeResponsavel : '',
        telefoneResponsavel: needsGuardian ? form.telefoneResponsavel : '',
        emailResponsavel: needsGuardian ? form.emailResponsavel : '',
      });
      setForm(initialForm);
      navigate('/login?cadastro=sucesso', { replace: true });
    } catch (requestError) {
      setError(requestError?.status === 409 ? 'Este e-mail já está cadastrado.' : 'Não foi possível concluir o cadastro. Tente novamente.');
    } finally {
      submittingRef.current = false;
      setLoading(false);
    }
  }

  return (
    <AuthLayout currentPage="cadastro">
      <main className="auth-registration">
        <div className="card auth-registration__card">
          <header className="auth-registration__header">
            <h1>Comece a usar a <span className="destaque-magenta">Palco</span></h1>
            <p>Crie sua conta para conectar artistas e contratantes.</p>
          </header>
          <form className="auth-registration__form" onSubmit={handleSubmit} noValidate>
            <div className="campo"><label className="campo__rotulo" htmlFor="cadastro-nome">Nome completo</label><input className="campo__input" id="cadastro-nome" name="nome" autoComplete="name" maxLength={150} value={form.nome} onChange={updateField} required /></div>
            <div className="campo"><label className="campo__rotulo" htmlFor="cadastro-nascimento">Data de nascimento</label><input className="campo__input" type="date" id="cadastro-nascimento" name="dataNascimento" value={form.dataNascimento} onChange={updateField} required /></div>
            <div className="campo"><label className="campo__rotulo" htmlFor="cadastro-telefone">Telefone</label><input className="campo__input" type="tel" id="cadastro-telefone" name="telefone" autoComplete="tel" maxLength={20} value={form.telefone} onChange={updateField} required /></div>
            <div className="campo"><label className="campo__rotulo" htmlFor="cadastro-email">E-mail</label><input className="campo__input" type="email" id="cadastro-email" name="email" autoComplete="email" maxLength={150} value={form.email} onChange={updateField} required /></div>
            <div className="campo"><label className="campo__rotulo" htmlFor="cadastro-tipo">Tipo de usuário</label><select className="campo__select auth-registration__select" id="cadastro-tipo" name="tipoUsuario" value={form.tipoUsuario} onChange={updateField}><option value="ARTISTA">Artista</option><option value="CONTRATANTE">Contratante</option></select></div>
            {form.tipoUsuario === 'CONTRATANTE' ? <div className="campo"><label className="campo__rotulo" htmlFor="cadastro-perfil">Tipo de perfil contratante</label><select className="campo__select auth-registration__select" id="cadastro-perfil" name="tipoPerfilContratante" value={form.tipoPerfilContratante} onChange={updateField}><option value="">Selecione (opcional)</option><option>Pessoa Física</option><option>Instituição pública</option><option>Instituição Privada</option><option>Produtora Cultural</option><option>Agência</option><option>Instituição de Ensino</option><option>ONG</option><option>Outro</option></select></div> : null}
            <div className="campo"><label className="campo__rotulo" htmlFor="cadastro-senha">Senha</label><input className="campo__input" type="password" id="cadastro-senha" name="senha" autoComplete="new-password" maxLength={72} value={form.senha} onChange={updateField} required /></div>
            <div className="campo"><label className="campo__rotulo" htmlFor="cadastro-confirmar-senha">Confirme sua senha</label><input className="campo__input" type="password" id="cadastro-confirmar-senha" name="confirmarSenha" autoComplete="new-password" maxLength={72} value={form.confirmarSenha} onChange={updateField} required /></div>
            {needsGuardian ? <fieldset className="auth-registration__guardian"><legend>Responsável legal</legend><div className="campo"><label className="campo__rotulo" htmlFor="cadastro-responsavel-nome">Nome do responsável</label><input className="campo__input" id="cadastro-responsavel-nome" name="nomeResponsavel" maxLength={150} value={form.nomeResponsavel} onChange={updateField} required /></div><div className="campo"><label className="campo__rotulo" htmlFor="cadastro-responsavel-telefone">Telefone do responsável</label><input className="campo__input" type="tel" id="cadastro-responsavel-telefone" name="telefoneResponsavel" maxLength={20} value={form.telefoneResponsavel} onChange={updateField} required /></div><div className="campo"><label className="campo__rotulo" htmlFor="cadastro-responsavel-email">E-mail do responsável</label><input className="campo__input" type="email" id="cadastro-responsavel-email" name="emailResponsavel" maxLength={150} value={form.emailResponsavel} onChange={updateField} required /></div></fieldset> : null}
            <label className="auth-registration__terms"><input type="checkbox" name="termos" checked={form.termos} onChange={updateField} /> Li e concordo com os <span className="auth-pending-link" role="link" aria-disabled="true">Termos de Uso</span> e a <span className="auth-pending-link" role="link" aria-disabled="true">Política de Privacidade</span>.</label>
            {error ? <p className="auth-feedback auth-feedback--error auth-registration__wide" role="alert">{error}</p> : null}
            <div className="auth-registration__actions"><button className="btn btn--primario" type="submit" disabled={loading}>{loading ? 'Registrando…' : 'Registrar'}</button></div>
          </form>
        </div>
      </main>
    </AuthLayout>
  );
}
