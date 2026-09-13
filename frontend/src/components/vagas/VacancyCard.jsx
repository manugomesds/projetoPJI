import {
  brazilianDate,
  vacancyRemuneration,
  enumLabel,
  textOr,
  vacancyImage,
} from './vacancyPresentation';

function replaceBrokenImage(event, vacancy, index) {
  const fallback = vacancyImage({ ...vacancy, fotos: [] }, index);
  if (!event.currentTarget.src.endsWith(fallback)) event.currentTarget.src = fallback;
}

function SearchVacancyCard({ vacancy, cancelled, index }) {
  const location = [textOr(vacancy.cidade, ''), textOr(vacancy.estado, '')]
    .filter(Boolean)
    .join('/');

  return (
    <article
      className={`vaga-busca-card${cancelled ? ' vaga-busca-card--cancelada' : ''}`}
      data-vaga-id={vacancy.id}
    >
      <img
        className="vaga-busca-card__imagem"
        src={vacancyImage(vacancy, index)}
        alt=""
        loading="lazy"
        onError={(event) => replaceBrokenImage(event, vacancy, index)}
      />
      <div className="vaga-busca-card__conteudo">
        <div className="vaga-busca-card__badges">
          {cancelled ? (
            <span className="vaga-busca-card__badge vaga-busca-card__badge--cancelada">
              Vaga Cancelada
            </span>
          ) : (
            <span className="vaga-busca-card__badge">
              {textOr(vacancy.categoria || vacancy.tipoContrato, 'Oportunidade')}
            </span>
          )}
          {vacancy.propriaDoContratante === true ? (
            <span className="vaga-busca-card__badge vaga-busca-card__badge--propria">Sua vaga</span>
          ) : null}
        </div>
        <h3 className="vaga-busca-card__titulo">{textOr(vacancy.titulo, 'Vaga sem título')}</h3>
        <p className="vaga-busca-card__empresa">
          {textOr(vacancy.nomeContratante, 'Contratante não informado')}
        </p>
        <div className="vaga-busca-card__metas">
          <span>{location || 'Local não informado'}</span>
          <span>{enumLabel(vacancy.modeloTrabalho) || 'Modelo não informado'}</span>
          <span>{textOr(vacancy.tipoContrato, 'Contrato não informado')}</span>
          <span>{vacancyRemuneration(vacancy)}</span>
        </div>
        <p className="vaga-busca-card__descricao">
          {textOr(vacancy.descricao, 'Descrição não informada.')}
        </p>
        <div className="vaga-busca-card__rodape">
          <p className="vaga-busca-card__prazo">{brazilianDate(vacancy.dataLimiteCandidatura)}</p>
          <a className="vaga-busca-card__link" href={`/vagas/${encodeURIComponent(vacancy.id)}`}>
            Ver detalhes
          </a>
        </div>
      </div>
    </article>
  );
}

function HomeVacancyCard({ vacancy, index, featured }) {
  const location = [textOr(vacancy.cidade, ''), textOr(vacancy.estado, '')]
    .filter(Boolean)
    .join(', ');
  const details = [location, enumLabel(vacancy.modeloTrabalho), vacancyRemuneration(vacancy)]
    .filter((value) => value && value !== 'Remuneração não informada');

  return (
    <li className={`vaga-mini${featured ? ' vaga-mini--destaque' : ''}`} data-vaga-id={vacancy.id}>
      <img
        className="vaga-mini__foto"
        src={vacancyImage(vacancy, index)}
        alt=""
        onError={(event) => replaceBrokenImage(event, vacancy, index)}
      />
      <div className="vaga-mini__corpo">
        <h2 className="vaga-mini__titulo">{textOr(vacancy.titulo, 'Vaga sem título')}</h2>
        <p className="vaga-mini__autor">
          {vacancy.propriaDoContratante === true ? 'Sua vaga · ' : ''}
          {textOr(vacancy.nomeContratante, 'Contratante')} ·{' '}
          {textOr(vacancy.categoria || vacancy.tipoContrato, 'Oportunidade')}
        </p>
        <p className="vaga-mini__local">{details.length ? details.join(' · ') : 'Detalhes na página da vaga'}</p>
        <p className="vaga-mini__descricao">
          <strong>Descrição</strong>{' '}
          {textOr(vacancy.descricao, 'Consulte os detalhes da oportunidade.')}
        </p>
        <a className="btn-palco btn-palco--amarelo vaga-mini__cta" href={`/vagas/${encodeURIComponent(vacancy.id)}`}>
          Ver vaga
        </a>
      </div>
    </li>
  );
}

export default function VacancyCard({ vacancy, variant = 'search', cancelled = false, index = 0, featured = false }) {
  return variant === 'home' ? (
    <HomeVacancyCard vacancy={vacancy} index={index} featured={featured} />
  ) : (
    <SearchVacancyCard vacancy={vacancy} cancelled={cancelled} index={index} />
  );
}
