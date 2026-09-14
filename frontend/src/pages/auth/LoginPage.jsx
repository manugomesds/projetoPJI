import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { saveSession } from '../../auth/sessionService';
import authService from '../../services/auth/authService';
import AuthLayout from './AuthLayout';

export function redirectToLegacyDashboard() {
  window.location.assign('/dashboard');
}

export default function LoginPage({ onAuthenticated = redirectToLegacyDashboard }) {
  useEffect(() => { document.title = 'Login — Palco'; }, []);
  const [searchParams] = useSearchParams();
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const submittingRef = useRef(false);
  const cadastroConcluido = searchParams.get('cadastro') === 'sucesso';

  async function handleSubmit(event) {
    event.preventDefault();
    if (submittingRef.current) return;
    if (!email.trim() || !senha) {
      setError('Preencha e-mail e senha para continuar.');
      return;
    }

    submittingRef.current = true;
    setLoading(true);
    setError('');
    try {
      const response = await authService.login({ email, senha });
      saveSession(response);
      setSenha('');
      onAuthenticated(response);
    } catch (requestError) {
      setError(
        requestError?.status === 401 || requestError?.status === 404
          ? 'E-mail ou senha incorretos.'
          : 'Não foi possível entrar agora. Tente novamente.'
      );
    } finally {
      submittingRef.current = false;
      setLoading(false);
    }
  }

  return (
    <AuthLayout currentPage="login">
      <main className="login auth-login">
        <div className="card login__card">
          <img className="auth-login__brand" src="/assets/home/logo-palco-branco.png" alt="" /><h1 className="login__titulo">Bem-vindo(a) a <span className="destaque-magenta">Palco</span></h1>
          {cadastroConcluido ? <p className="auth-feedback auth-feedback--success" role="status">Cadastro realizado com sucesso. Faça login.</p> : null}
          <form className="login__form" onSubmit={handleSubmit} noValidate>
            <div className="campo">
              <label className="campo__rotulo" htmlFor="login-email">E-mail</label>
              <input className="campo__input" type="email" id="login-email" autoComplete="email" maxLength={150} value={email} onChange={(event) => setEmail(event.target.value)} required />
            </div>
            <div className="campo">
              <label className="campo__rotulo" htmlFor="login-senha">Senha</label>
              <div className="campo__controle">
                <input className="campo__input campo__input--com-icone" type={showPassword ? 'text' : 'password'} id="login-senha" autoComplete="current-password" maxLength={72} value={senha} onChange={(event) => setSenha(event.target.value)} required />
                <button className="campo__olho auth-password-toggle" type="button" aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'} onClick={() => setShowPassword((visible) => !visible)}>
                  <img src="/assets/icone-olho.png" alt="" />
                </button>
              </div>
              <a className="campo__ajuda login__esqueceu" href="/recuperar-senha">Esqueceu a senha?</a>
            </div>
            {error ? <p className="auth-feedback auth-feedback--error" role="alert">{error}</p> : null}
            <button className="btn btn--primario login__entrar" type="submit" disabled={loading}>{loading ? 'Entrando…' : 'Entrar'}</button>
            <a className="btn btn--contorno login__google" href="/google-callback.html">Acessar com Google</a>
            <p className="login__cadastro">Ainda não está no <span className="destaque-magenta">Palco</span>? <a href="/cadastro">Crie uma conta.</a></p>
            <p className="texto-legal login__legal">Ao continuar, você concorda com os <span className="auth-pending-link" role="link" aria-disabled="true">Termos de Serviço do Palco</span> e confirma que leu nossa <span className="auth-pending-link" role="link" aria-disabled="true">Política de Privacidade</span>.</p>
          </form>
        </div>
      </main>
    </AuthLayout>
  );
}
