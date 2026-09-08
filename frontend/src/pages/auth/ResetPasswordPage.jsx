import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import ErrorState from '../../components/common/ErrorState';
import LoadingState from '../../components/common/LoadingState';
import ApiError from '../../services/api/ApiError';
import {
  INVALID_RESET_LINK_MESSAGE,
  readResetToken,
  redirectToLegacyLogin,
  resetPassword,
  sanitizeResetUrl,
} from '../../services/auth/passwordRecoveryService';
import PasswordRecoveryLayout from './PasswordRecoveryLayout';
import { isPasswordValid, PASSWORD_POLICY_MESSAGE } from '../../utils/passwordPolicy';

const TECHNICAL_ERROR = new Error('Tente novamente em instantes.');

export default function ResetPasswordPage() {
  const [initialToken] = useState(() => readResetToken());
  const token = useRef(initialToken);
  const requestInFlight = useRef(false);
  const [status, setStatus] = useState(initialToken ? 'idle' : 'missing-token');
  const [passwords, setPasswords] = useState({ novaSenha: '', confirmarSenha: '' });

  useLayoutEffect(() => {
    sanitizeResetUrl();
  }, []);

  useEffect(() => {
    document.title = 'Redefinir senha — Palco';
  }, []);

  function handleChange(event) {
    const { name, value } = event.target;
    setPasswords((current) => ({ ...current, [name]: value }));
    if (status === 'mismatch' || status === 'policy') setStatus('idle');
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const form = event.currentTarget;

    if (!token.current || requestInFlight.current) return;
    if (!isPasswordValid(passwords.novaSenha)) {
      setStatus('policy');
      return;
    }
    if (!form.checkValidity()) {
      form.reportValidity();
      return;
    }
    if (passwords.novaSenha !== passwords.confirmarSenha) {
      setStatus('mismatch');
      return;
    }

    requestInFlight.current = true;
    setStatus('loading');

    try {
      await resetPassword(token.current, passwords.novaSenha);
      token.current = null;
      setPasswords({ novaSenha: '', confirmarSenha: '' });
      setStatus('success');
      redirectToLegacyLogin();
    } catch (error) {
      if (error instanceof ApiError && error.status === 400 && error.message === PASSWORD_POLICY_MESSAGE) {
        setStatus('policy');
      } else {
        setStatus(error instanceof ApiError && [400, 404].includes(error.status) ? 'invalid' : 'error');
      }
    } finally {
      requestInFlight.current = false;
    }
  }

  const submitting = status === 'loading';
  const unavailable = !initialToken || submitting || status === 'success';

  return (
    <PasswordRecoveryLayout>
      <main className="login">
        <div className="card login__card">
          <h1 className="login__titulo">
            Redefinir <span className="destaque-magenta">senha</span>
          </h1>

          <form
            className="login__form"
            noValidate
            aria-busy={submitting}
            onSubmit={handleSubmit}
          >
            <div className="campo">
              <label className="campo__rotulo" htmlFor="nova-senha">
                Nova senha
              </label>
              <div className="campo__controle">
                <input
                  className="campo__input"
                  type="password"
                  id="nova-senha"
                  name="novaSenha"
                  autoComplete="new-password"
                  maxLength={72}
                  required
                  value={passwords.novaSenha}
                  onChange={handleChange}
                />
              </div>
            </div>

            <div className="campo">
              <label className="campo__rotulo" htmlFor="confirmar-senha">
                Confirmar nova senha
              </label>
              <div className="campo__controle">
                <input
                  className="campo__input"
                  type="password"
                  id="confirmar-senha"
                  name="confirmarSenha"
                  autoComplete="new-password"
                  maxLength={72}
                  required
                  value={passwords.confirmarSenha}
                  onChange={handleChange}
                />
              </div>
            </div>

            <button
              className="btn btn--primario login__entrar"
              type="submit"
              disabled={unavailable}
            >
              Redefinir senha
            </button>

            {submitting ? (
              <LoadingState
                message="Redefinindo senha…"
                className="auth-recovery__state"
                indicatorClassName="react-state__indicator"
              />
            ) : null}

            {status === 'missing-token' ? (
              <p className="auth-recovery__message auth-recovery__message--error" role="alert">
                Link de recuperação inválido ou incompleto.
              </p>
            ) : null}

            {status === 'mismatch' ? (
              <p className="auth-recovery__message auth-recovery__message--error" role="alert">
                As senhas informadas não são iguais.
              </p>
            ) : null}

            {status === 'policy' ? (
              <p className="auth-recovery__message auth-recovery__message--error" role="alert">
                {PASSWORD_POLICY_MESSAGE}
              </p>
            ) : null}

            {status === 'invalid' ? (
              <p className="auth-recovery__message auth-recovery__message--error" role="alert">
                {INVALID_RESET_LINK_MESSAGE}
              </p>
            ) : null}

            {status === 'success' ? (
              <p className="auth-recovery__message" role="status" aria-live="polite">
                Senha redefinida com sucesso. Encaminhando para o login…
              </p>
            ) : null}

            {status === 'error' ? (
              <ErrorState
                error={TECHNICAL_ERROR}
                title="Não foi possível redefinir a senha."
                className="auth-recovery__state auth-recovery__state--error"
                headingLevel="h2"
              />
            ) : null}

            <p className="login__cadastro">
              <a href="/login">Voltar ao login</a> ·{' '}
              <a href="/recuperar-senha">Solicitar novo link</a>
            </p>
          </form>
        </div>
      </main>
    </PasswordRecoveryLayout>
  );
}
