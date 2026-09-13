import apiClient from '../api/apiClient';

export const DASHBOARD_SIZE = 5;

export function getDashboard(size = DASHBOARD_SIZE) {
  return apiClient.get(`/dashboard?size=${size}`);
}

export async function getPrivateProfile() {
  const usuario = await apiClient.get('/usuarios/me');
  const artista = usuario.tipoUsuario === 'ARTISTA';
  const [perfil, tags] = await Promise.all([
    apiClient.get(artista
      ? `/perfis-artistas/${usuario.id}`
      : `/perfis-contratantes/${usuario.id}`),
    artista ? apiClient.get('/funcoes') : Promise.resolve([]),
  ]);
  return { usuario, perfil, tags };
}

function profilePayload(usuario, values) {
  const shared = {
    usuarioId: usuario.id,
    biografia: values.biografia.trim(),
    localizacao: values.localizacao.trim(),
    bannerUrl: values.bannerUrl.trim() || null,
  };

  if (usuario.tipoUsuario === 'ARTISTA') {
    return {
      ...shared,
      urlPortfolio: values.urlPortfolio.trim() || null,
      ...(values.areaPrincipalId ? { areaPrincipalId: Number(values.areaPrincipalId), funcaoIds: values.funcaoIds.map(Number) } : {}),
    };
  }

  return {
    ...shared,
    nomeEmpresa: values.nomeEmpresa.trim() || null,
    tipoPerfil: values.tipoPerfil.trim() || null,
  };
}

export async function updatePrivateProfile(usuario, values) {
  const profilePath = usuario.tipoUsuario === 'ARTISTA'
    ? `/perfis-artistas/${usuario.id}`
    : `/perfis-contratantes/${usuario.id}`;

  await apiClient.put(profilePath, profilePayload(usuario, values));
  return apiClient.put('/usuarios/me', {
    nome: values.nome.trim(),
    dataNascimento: usuario.dataNascimento,
    telefone: values.telefone.trim(),
    email: values.email.trim(),
    senhaAtual: values.senhaAtual || null,
    novaSenha: values.novaSenha || null,
  });
}

const accountService = { getDashboard, getPrivateProfile, updatePrivateProfile };

export default accountService;
