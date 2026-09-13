import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import sessionService from '../../auth/sessionService';
import AccountLayout from '../../components/account/AccountLayout';
import { getPrivateProfile, updatePrivateProfile } from '../../services/account/accountService';
import { isPasswordValid, PASSWORD_POLICY_MESSAGE } from '../../utils/passwordPolicy';

const EMPTY_VALUES = {
  nome: '', telefone: '', email: '', biografia: '', localizacao: '', bannerUrl: '',
  urlPortfolio: '', funcaoIds: [], nomeEmpresa: '', tipoPerfil: '', senhaAtual: '', novaSenha: '',
};

function toValues(usuario, perfil) {
  return {
    ...EMPTY_VALUES,
    nome: usuario.nome || '',
    telefone: usuario.telefone || '',
    email: usuario.email || '',
    biografia: perfil.biografia || '',
    localizacao: perfil.localizacao || '',
    bannerUrl: perfil.bannerUrl || '',
    urlPortfolio: perfil.urlPortfolio || '',
    areaPrincipalId: perfil.areaPrincipalId || null,
    funcaoIds: (perfil.funcaoIds || []).map(Number),
    nomeEmpresa: perfil.nomeEmpresa || '',
    tipoPerfil: perfil.tipoPerfil || '',
  };
}

function fieldError(error) {
  if (error?.status === 403) return 'A senha atual está incorreta ou você não tem permissão para esta alteração.';
  if (error?.status === 409) return 'O e-mail informado já está cadastrado.';
  if (error?.status === 400 || error?.status === 422) return error.message || 'Revise os dados informados.';
  return 'Não foi possível atualizar o perfil. Tente novamente.';
}

export default function ProfilePage() {
  const navigate = useNavigate();
  const submitting = useRef(false);
  const [data, setData] = useState(null);
  const [values, setValues] = useState(EMPTY_VALUES);
  const [state, setState] = useState({ loading: true, saving: false, error: '', success: '' });

  useEffect(() => {
    let active = true;
    getPrivateProfile()
      .then((result) => {
        if (!active) return;
        setData(result);
        setValues(toValues(result.usuario, result.perfil));
        setState({ loading: false, saving: false, error: '', success: '' });
      })
      .catch(() => { if (active) setState({ loading: false, saving: false, error: 'Não foi possível carregar seu perfil.', success: '' }); });
    return () => { active = false; };
  }, []);

  const artist = data?.usuario.tipoUsuario === 'ARTISTA';


  function change(event) {
    const { name, value } = event.target;
    setValues((current) => ({ ...current, [name]: value }));
  }

  function toggleTag(id) {
    setValues((current) => ({
      ...current,
      funcaoIds: current.funcaoIds.includes(id)
        ? current.funcaoIds.filter((tagId) => tagId !== id)
        : [...current.funcaoIds, id],
    }));
  }

  async function submit(event) {
    event.preventDefault();
    if (submitting.current || !data) return;
    if (!event.currentTarget.checkValidity()) {
      setState((current) => ({ ...current, error: 'Preencha os campos obrigatórios.', success: '' }));
      return;
    }
    if (values.novaSenha && !values.senhaAtual) {
      setState((current) => ({ ...current, error: 'Informe a senha atual para definir uma nova senha.', success: '' }));
      return;
    }
    if (values.novaSenha && !isPasswordValid(values.novaSenha)) {
      setState((current) => ({ ...current, error: PASSWORD_POLICY_MESSAGE, success: '' }));
      return;
    }

    submitting.current = true;
    setState((current) => ({ ...current, saving: true, error: '', success: '' }));
    try {
      const updatedUser = await updatePrivateProfile(data.usuario, values);
      const emailChanged = updatedUser.email !== data.usuario.email;
      setData((current) => ({ ...current, usuario: updatedUser }));
      setValues((current) => ({ ...current, senhaAtual: '', novaSenha: '' }));
      const session = sessionService.getSession();
      if (session?.token) sessionService.saveSession({ ...session, nome: updatedUser.nome, email: updatedUser.email, perfilCompleto: updatedUser.perfilCompleto });
      if (emailChanged) {
        sessionService.clearLocalSession();
        navigate('/login', { replace: true });
        return;
      }
      setState({ loading: false, saving: false, error: '', success: 'Perfil atualizado com sucesso.' });
    } catch (error) {
      setState({ loading: false, saving: false, error: fieldError(error), success: '' });
    } finally {
      submitting.current = false;
    }
  }

  if (state.loading) return <AccountLayout><main className="account-main"><section className="account-state" aria-live="polite">Carregando perfil…</section></main></AccountLayout>;
  if (!data) return <AccountLayout><main className="account-main"><section className="account-state account-state--error" role="alert"><h1>Perfil indisponível</h1><p>{state.error}</p></section></main></AccountLayout>;

  return (
    <AccountLayout>
      <main className="account-main account-main--profile">
        <header className="account-heading"><div><p className="account-eyebrow">RF08</p><h1>Meu perfil</h1><p>Edite somente seus dados. A completude é confirmada pelo servidor.</p></div></header>
        <section className="profile-summary" aria-label="Resumo do perfil">
          {data.perfil.avatarUrl ? <img src={data.perfil.avatarUrl} alt="Seu avatar atual" /> : null}
          <div><strong>{data.usuario.perfilCompleto ? 'Perfil completo' : 'Perfil incompleto'}</strong><p>A situação acima é confirmada após salvar seus dados.</p></div>
        </section>
        {values.bannerUrl ? <img className="profile-banner" src={values.bannerUrl} alt="Prévia do banner do perfil" /> : null}
        <form className="account-form" onSubmit={submit} noValidate>
          <fieldset><legend>Dados cadastrais</legend>
            <label>Nome<input name="nome" value={values.nome} onChange={change} maxLength={150} required /></label>
            <label>Data de nascimento<input value={data.usuario.dataNascimento || ''} type="date" readOnly aria-readonly="true" /></label>
            <label>Telefone<input name="telefone" value={values.telefone} onChange={change} maxLength={20} required /></label>
            <label>E-mail<input name="email" value={values.email} onChange={change} type="email" maxLength={150} required /></label>
          </fieldset>
          <fieldset><legend>Apresentação</legend>
            {artist ? null : <><label>Nome da empresa<input name="nomeEmpresa" value={values.nomeEmpresa} onChange={change} maxLength={150} /></label><label>Tipo de perfil<input name="tipoPerfil" value={values.tipoPerfil} onChange={change} maxLength={100} /></label></>}
            <label className="account-form__wide">Biografia<textarea name="biografia" value={values.biografia} onChange={change} required /></label>
            <label>Localização<input name="localizacao" value={values.localizacao} onChange={change} maxLength={150} required /></label>
            {artist ? <label>URL do portfólio<input name="urlPortfolio" value={values.urlPortfolio} onChange={change} type="url" maxLength={255} /></label> : null}
            <label className="account-form__wide">URL do banner<input name="bannerUrl" value={values.bannerUrl} onChange={change} type="url" maxLength={255} /></label>
          </fieldset>
          {artist ? <fieldset className="account-form__wide"><legend>Funções artísticas</legend><div className="profile-tags">{data.tags.filter(tag => Number(tag.areaId) === Number(values.areaPrincipalId)).map((tag) => <label key={tag.id}><input type="checkbox" checked={values.funcaoIds.includes(Number(tag.id))} onChange={() => toggleTag(Number(tag.id))} />{tag.nome}</label>)}</div><small>Funções do catálogo oficial. A mudança da área principal e as especializações ainda não estão disponíveis.</small></fieldset> : null}
          <fieldset><legend>Troca de senha</legend>
            <label>Senha atual<input name="senhaAtual" value={values.senhaAtual} onChange={change} type="password" maxLength={72} autoComplete="current-password" /></label>
            <label>Nova senha<input name="novaSenha" value={values.novaSenha} onChange={change} type="password" maxLength={72} autoComplete="new-password" /></label>
            <p className="account-form__hint account-form__wide">A senha é enviada apenas nesta alteração, processada com BCrypt pelo backend e nunca salva no navegador.</p>
          </fieldset>
          {state.error ? <p className="account-feedback account-feedback--error" role="alert">{state.error}</p> : null}
          {state.success ? <p className="account-feedback account-feedback--success" role="status">{state.success}</p> : null}
          <div className="account-form__actions"><a href="/dashboard">Cancelar</a><button className="btn btn--primario" type="submit" disabled={state.saving}>{state.saving ? 'Salvando…' : 'Salvar perfil'}</button></div>
        </form>
        <section className="perfil__zona-risco"><h2>Encerrar conta</h2><p>A exclusão de conta está indisponível nesta versão.</p><a href="/excluir-conta.html">Opções de encerramento</a></section>
      </main>
    </AccountLayout>
  );
}
