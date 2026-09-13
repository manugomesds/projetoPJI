export const SESSION_STORAGE_KEY = 'palco.sessao';
export const VACANCY_DRAFT_STORAGE_KEY = 'palco.vaga.rascunho';

function storages() {
  return {
    session: window.sessionStorage,
    local: window.localStorage,
  };
}

export function clearLocalSession() {
  const { session, local } = storages();
  session.removeItem(SESSION_STORAGE_KEY);
  local.removeItem(SESSION_STORAGE_KEY);
  session.removeItem(VACANCY_DRAFT_STORAGE_KEY);
}

export function getSession() {
  const { session, local } = storages();
  const serializedSession =
    session.getItem(SESSION_STORAGE_KEY) || local.getItem(SESSION_STORAGE_KEY);

  if (!serializedSession) return null;

  try {
    const saved = JSON.parse(serializedSession);
    if (saved?.status === 'AGUARDANDO_DADOS' || (saved?.statusConta && saved.statusConta !== 'ATIVA')) { clearLocalSession(); return null; }
    return saved;
  } catch {
    clearLocalSession();
    return null;
  }
}

export function saveSession(authResponse) {
  if (authResponse?.status === 'AGUARDANDO_DADOS' || (authResponse?.statusConta && authResponse.statusConta !== 'ATIVA')) {
    clearLocalSession();
    throw new Error('Conta pendente. Conclua os dados antes de entrar.');
  }
  const { session, local } = storages();
  const allowedFields = [
    'token',
    'id',
    'nome',
    'email',
    'tipoUsuario',
    'perfilCompleto',
    'avatarUrl',
    'refreshToken',
    'statusConta',
  ];
  const safeSession = allowedFields.reduce((result, field) => {
    if (authResponse?.[field] !== undefined) result[field] = authResponse[field];
    return result;
  }, {});

  if (!safeSession.token) {
    throw new Error('A resposta de autenticação não contém um token válido.');
  }

  session.setItem(SESSION_STORAGE_KEY, JSON.stringify(safeSession));
  local.removeItem(SESSION_STORAGE_KEY);
  return safeSession;
}

export function getAccessToken() {
  return getSession()?.token || null;
}

export function isAuthenticated() {
  return Boolean(getAccessToken());
}

const sessionService = {
  getSession,
  getAccessToken,
  isAuthenticated,
  saveSession,
  clearLocalSession,
};

export default sessionService;
