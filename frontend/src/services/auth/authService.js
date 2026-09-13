import apiClient from '../api/apiClient';

export function login({ email, senha }) {
  return apiClient.post('/auth/login', {
    email: email.trim(),
    senha,
    rememberMe: false,
  }, { token: null });
}

export function cadastrar({
  nome,
  dataNascimento,
  telefone,
  email,
  senha,
  tipoUsuario,
  tipoPerfilContratante,
  tipoPerfilArtistico,
  areaPrincipalId,
  nomeResponsavel,
  telefoneResponsavel,
  emailResponsavel,
}) {
  const menor = Boolean(nomeResponsavel || telefoneResponsavel || emailResponsavel);
  return apiClient.post('/auth/cadastro', {
    nome: nome.trim(),
    dataNascimento,
    telefone: telefone.trim(),
    email: email.trim(),
    senha,
    tipoUsuario,
    ...(tipoUsuario === 'ARTISTA' ? { tipoPerfilArtistico, areaPrincipalId: Number(areaPrincipalId) } : {}),
    tipoPerfilContratante: tipoUsuario === 'CONTRATANTE' ? tipoPerfilContratante || null : null,
    nomeResponsavel: menor ? nomeResponsavel.trim() : null,
    telefoneResponsavel: menor ? telefoneResponsavel.trim() : null,
    emailResponsavel: menor ? emailResponsavel.trim() : null,
  }, { token: null });
}

const authService = { login, cadastrar };

export default authService;
