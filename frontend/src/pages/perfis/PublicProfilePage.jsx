import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import sessionService from '../../auth/sessionService';
import ErrorState from '../../components/common/ErrorState';
import LoadingState from '../../components/common/LoadingState';
import ApiError from '../../services/api/ApiError';
import PortfolioSection from '../../components/portfolio/PortfolioSection';
import {
  createConversation,
  getPublicProfile,
  navigateToMessages,
} from '../../services/perfis/publicProfileService';

const VALID_ID = /^[1-9]\d*$/;
const VALID_TYPES = new Set(['ARTISTA', 'CONTRATANTE']);

export function safeHttpUrl(value) {
  if (!value || typeof value !== 'string') return null;

  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null;
  } catch {
    return null;
  }
}

function PublicProfileHeader() {
  return (
    <header className="perfil-publico-topo">
      <nav className="perfil-publico-nav" aria-label="Navegação principal">
        <a className="perfil-publico-nav__marca" href="/login" aria-label="Palco — início">
          <img src="/assets/logo-palco.png" alt="Palco" />
        </a>
        <div className="perfil-publico-nav__acoes">
          <a href="/login">Entrar</a>
          <a className="perfil-publico-nav__cadastro" href="/cadastro">
            Criar conta
          </a>
        </div>
      </nav>
    </header>
  );
}

function ProfileError({ title, message }) {
  return (
    <ErrorState
      title={title}
      error={new Error(message)}
      className="perfil-publico__estado perfil-publico__estado--erro"
      headingLevel="h1"
    >
      <a className="perfil-publico__voltar" href="/login">
        Voltar para a Palco
      </a>
    </ErrorState>
  );
}

function ProfileCard({ profile, type }) {
  const [activeTab, setActiveTab] = useState('sobre');
  const [chatState, setChatState] = useState({ loading: false, error: '' });
  const session = useMemo(() => sessionService.getSession(), []);
  const name = profile.nomeExibicao || 'Perfil Palco';
  const avatarUrl = safeHttpUrl(profile.avatarUrl);
  const bannerUrl = safeHttpUrl(profile.bannerUrl);
  const portfolioUrl = type === 'ARTISTA' ? safeHttpUrl(profile.urlPortfolio) : null;
  const tags =
    type === 'ARTISTA' && Array.isArray(profile.funcoes)
      ? profile.funcoes.filter((tag) => typeof tag?.nome === 'string' && tag.nome.trim())
      : [];
  const canChat = Boolean(
    session?.token && profile.usuarioId && session.tipoUsuario !== type
  );

  async function handleChat() {
    setChatState({ loading: true, error: '' });

    try {
      const room = await createConversation(profile.usuarioId);
      navigateToMessages(room.salaId);
    } catch (error) {
      setChatState({
        loading: false,
        error: error?.message || 'Não foi possível iniciar a conversa.',
      });
    }
  }

  return (
    <article className="perfil-publico__cartao">
      <div className="perfil-publico__banner">
        {bannerUrl && <img src={bannerUrl} alt={`Banner de ${name}`} />}
      </div>

      <div className="perfil-publico__conteudo">
        <div className="perfil-publico__identidade">
          {avatarUrl && <img className="perfil-publico__avatar" src={avatarUrl} alt={`Avatar de ${name}`} />}
          <div>
            <p className="perfil-publico__tipo">{type}</p>
            <h1>{name}</h1>
            {profile.localizacao && <p className="perfil-publico__local">{profile.localizacao}</p>}
            {canChat && (
              <button
                className="perfil-publico__mensagem"
                type="button"
                disabled={chatState.loading}
                onClick={handleChat}
              >
                Enviar mensagem
              </button>
            )}
            {chatState.error && (
              <p className="perfil-publico__mensagem-erro" role="alert">
                {chatState.error}
              </p>
            )}
          </div>
        </div>

        {tags.length > 0 && (
          <div className="perfil-publico__tags" aria-label="Áreas de atuação">
            {tags.map((tag, index) => (
              <span className="perfil-publico__tag" key={tag.id ?? `${tag.nome}-${index}`}>
                {tag.nome}
              </span>
            ))}
          </div>
        )}

        <div className="perfil-publico__abas" role="tablist" aria-label="Informações do perfil">
          <button
            id="aba-sobre"
            className="perfil-publico__aba"
            type="button"
            role="tab"
            aria-selected={activeTab === 'sobre'}
            aria-controls="painel-sobre"
            onClick={() => setActiveTab('sobre')}
          >
            Sobre
          </button>
          {type === 'ARTISTA' && (
            <button
              id="aba-portfolio"
              className="perfil-publico__aba"
              type="button"
              role="tab"
              aria-selected={activeTab === 'portfolio'}
              aria-controls="painel-portfolio"
              onClick={() => setActiveTab('portfolio')}
            >
              Portfólio
            </button>
          )}
        </div>

        <section
          id="painel-sobre"
          className="perfil-publico__painel"
          role="tabpanel"
          aria-labelledby="aba-sobre"
          hidden={activeTab !== 'sobre'}
        >
          <h2>Sobre</h2>
          <p>{profile.biografia || 'Este perfil ainda não adicionou uma apresentação.'}</p>
          {type === 'CONTRATANTE' && profile.tipoPerfil && (
            <dl className="perfil-publico__detalhes">
              <div>
                <dt>Tipo de perfil</dt>
                <dd>{profile.tipoPerfil}</dd>
              </div>
            </dl>
          )}
        </section>

        {type === 'ARTISTA' && (
          <section
            id="painel-portfolio"
            className="perfil-publico__painel"
            role="tabpanel"
            aria-labelledby="aba-portfolio"
            hidden={activeTab !== 'portfolio'}
          >
            {portfolioUrl ? <>
            <p>O portfólio completo será aberto em uma nova aba.</p>
            <a
              className="perfil-publico__portfolio-link"
              href={portfolioUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              Acessar portfólio
            </a>
            </> : null}
            {activeTab === 'portfolio' ? <PortfolioSection artistId={profile.usuarioId} /> : null}
          </section>
        )}
      </div>
    </article>
  );
}

export default function PublicProfilePage() {
  const { tipo, id } = useParams();
  const type = typeof tipo === 'string' ? tipo.toUpperCase() : '';
  const validRoute = VALID_TYPES.has(type) && typeof id === 'string' && VALID_ID.test(id);
  const [state, setState] = useState({ status: 'loading' });

  useEffect(() => {
    document.body.classList.add('perfil-publico-pagina');
    return () => document.body.classList.remove('perfil-publico-pagina');
  }, []);

  useEffect(() => {
    document.title = 'Perfil público — Palco';
    if (!validRoute) return undefined;

    let active = true;
    setState({ status: 'loading' });

    getPublicProfile(type, id)
      .then((profile) => {
        if (!active) return;
        document.title = `${profile.nomeExibicao || 'Perfil público'} — Palco`;
        setState({ status: 'success', profile });
      })
      .catch((error) => {
        if (!active) return;
        setState({
          status: error instanceof ApiError && error.status === 404 ? 'not-found' : 'error',
        });
      });

    return () => {
      active = false;
    };
  }, [id, type, validRoute]);

  let content;

  if (!validRoute) {
    content = (
      <ProfileError
        title="Endereço de perfil inválido"
        message="Informe um tipo e um identificador de perfil válidos."
      />
    );
  } else if (state.status === 'loading') {
    content = (
      <LoadingState
        message="Carregando perfil público..."
        className="perfil-publico__estado"
        indicatorClassName="perfil-publico__spinner"
      />
    );
  } else if (state.status === 'not-found') {
    content = (
      <ProfileError
        title="Perfil não encontrado"
        message="Este perfil não existe ou não está disponível publicamente."
      />
    );
  } else if (state.status === 'error') {
    content = (
      <ProfileError
        title="Não foi possível carregar o perfil"
        message="Tente novamente em alguns instantes."
      />
    );
  } else {
    content = <ProfileCard key={`${type}-${state.profile.usuarioId}`} profile={state.profile} type={type} />;
  }

  return (
    <>
      <PublicProfileHeader />
      <main className="perfil-publico" aria-live="polite">
        {content}
      </main>
    </>
  );
}

export { VALID_ID, VALID_TYPES };
