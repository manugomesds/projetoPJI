import { vacancyRemuneration } from './vacancyPresentation';
const STATUS_LABELS = {
  ABERTA: 'Em seleção',
  PAUSADA: 'Pausada',
  ENCERRADA: 'Concluída',
  CANCELADA: 'Cancelada',
};

const currencyFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

function formatCurrency(value) {
  const amount = Number(value);
  return Number.isFinite(amount) ? currencyFormatter.format(amount) : '';
}

export default function VagaDetails({ vaga }) {
  const location = [vaga.cidade, vaga.estado].filter(Boolean).join('/');
  const summary = [location, vaga.modeloTrabalho, vacancyRemuneration(vaga)]
    .filter(Boolean)
    .join(' · ');
  const funcaoIds = Array.isArray(vaga.funcaoIds) ? vaga.funcaoIds : [];

  return (
    <article className="vaga-publica">
      <p className="dashboard__sobrelinha">{STATUS_LABELS[vaga.status] || vaga.status}</p>
      <h1 className="vaga-publica__titulo">{vaga.titulo}</h1>
      <p className="vaga-publica__empresa">{vaga.nomeContratante}</p>
      {summary && <div className="vaga-publica__resumo">{summary}</div>}
      <section>
        <h2>Sobre a oportunidade</h2>
        <p>{vaga.descricao}</p>
      </section>
      <section>
        <h2>Requisitos</h2>
        <p>{vaga.requisitos}</p>
      </section>
      {funcaoIds.length > 0 && (
        <div className="dashboard-card__tags" aria-label="Funções da vaga">
          {funcaoIds.map((tagId) => (
            <span className="dashboard-card__tag" key={tagId}>
              Função #{tagId}
            </span>
          ))}
        </div>
      )}
    </article>
  );
}

export { formatCurrency };
