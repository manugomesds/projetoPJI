const currencyFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

export const VACANCY_FALLBACK_IMAGES = [
  '/assets/vaga-foto-1.png',
  '/assets/home/hero-vaga-jardim.png',
  '/assets/home/hero-vaga-bar.png',
];

export function textOr(value, fallback = 'Não informado') {
  return value === null || value === undefined || String(value).trim() === ''
    ? fallback
    : String(value).trim();
}

export function enumLabel(value) {
  return textOr(value, '')
    .toLowerCase()
    .replace(/(^|_)(\p{L})/gu, (_, separator, letter) => `${separator ? ' ' : ''}${letter.toUpperCase()}`);
}

export function currency(value) {
  if (value === null || value === undefined || value === '') return 'Remuneração não informada';
  const amount = Number(value);
  return Number.isFinite(amount) ? currencyFormatter.format(amount) : 'Remuneração não informada';
}

export function brazilianDate(value) {
  if (!value) return 'Prazo não informado';
  const parts = String(value).substring(0, 10).split('-');
  return parts.length === 3 ? `Prazo até ${parts[2]}/${parts[1]}/${parts[0]}` : `Prazo ${value}`;
}

export function safeHttpUrl(value) {
  if (!value || typeof value !== 'string') return null;
  try {
    const url = new URL(value, window.location.href);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null;
  } catch {
    return null;
  }
}

export function vacancyImage(vacancy, index = 0) {
  const validPhoto = Array.isArray(vacancy?.fotos)
    ? vacancy.fotos.map(safeHttpUrl).find(Boolean)
    : null;
  const numericId = Number(vacancy?.id);
  const position = Number.isFinite(numericId) ? Math.abs(numericId) : index;
  return validPhoto || VACANCY_FALLBACK_IMAGES[position % VACANCY_FALLBACK_IMAGES.length];
}

export function vacancyRemuneration(vacancy) {
  const parts = [];
  if (vacancy.valorMinimo != null) parts.push(currency(vacancy.valorMinimo));
  if (vacancy.valorMaximo != null && vacancy.valorMaximo !== vacancy.valorMinimo) parts.push(currency(vacancy.valorMaximo));
  if (!parts.length && vacancy.remuneraValor != null) parts.push(currency(vacancy.remuneraValor));
  if (vacancy.formaRemuneracao) parts.push(enumLabel(vacancy.formaRemuneracao));
  else if (vacancy.formaPagamento) parts.push(vacancy.formaPagamento);
  return parts.join(' · ') || 'Remuneração não informada';
}
