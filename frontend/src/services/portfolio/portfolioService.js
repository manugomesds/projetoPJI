import apiClient, { buildUrl } from '../api/apiClient';

const TYPES = { jpg: ['image/jpeg', 5], jpeg: ['image/jpeg', 5], png: ['image/png', 5], pdf: ['application/pdf', 10], mp3: ['audio/mpeg', 20] };
export const PORTFOLIO_LIMITS = 'JPG/JPEG/PNG até 5 MB; PDF até 10 MB; MP3 até 20 MB. Vídeos somente por link YouTube ou Vimeo.';
export function validateFile(file) {
  if (!file) return 'Selecione um arquivo.';
  const ext = file.name.split('.').pop().toLowerCase();
  const rule = Object.prototype.hasOwnProperty.call(TYPES, ext) ? TYPES[ext] : null;
  if (!rule || !file.name.includes('.')) return 'Formato não permitido. Use JPG, JPEG, PNG, PDF ou MP3.';
  if (!file.size || file.size > rule[1] * 1024 * 1024) return `Arquivo vazio ou acima do limite de ${rule[1]} MB.`;
  if (file.type && file.type !== rule[0]) return 'Tipo aparente incompatível com a extensão do arquivo.';
  return '';
}
export function listPortfolio(owner, artistId, kind, page = 0, signal) {
  const path = owner ? '/portfolio/me' : `/portfolio/publico/artistas/${artistId}`;
  return apiClient.get(`${path}/${kind}?${new URLSearchParams({ page, size: 20 })}`, { signal, ...(owner ? {} : { token: null }) });
}
export function uploadFile(file) {
  const form = new FormData(); form.append('arquivo', file);
  return apiClient.post('/portfolio/arquivos', form);
}
export function deleteItem(kind, id) { return apiClient.delete(`/portfolio/${kind}/${id}`); }
export function addVideo(url) { return apiClient.post('/portfolio/videos', { url }); }
export function fileContentPath(id, owner) {
  if (!/^[1-9]\d*$/.test(String(id))) return null;
  return `/portfolio/${owner ? '' : 'publico/'}arquivos/${id}/conteudo`;
}
export function publicContentUrl(id) { const path = fileContentPath(id, false); return path ? buildUrl(path) : null; }
export function privateContent(id, signal) { return apiClient.get(fileContentPath(id, true), { responseType: 'blob', signal }); }
export function safeEmbedUrl(value) {
  return typeof value === 'string' && /^https:\/\/(www\.youtube-nocookie\.com\/embed\/[A-Za-z0-9_-]{11}|player\.vimeo\.com\/video\/[1-9]\d{0,11})$/.test(value) ? value : null;
}
