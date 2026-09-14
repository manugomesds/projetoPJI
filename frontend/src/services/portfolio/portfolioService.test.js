import apiClient from '../api/apiClient';
import { addVideo, fileContentPath, listPortfolio, safeEmbedUrl, uploadFile, validateFile } from './portfolioService';

jest.mock('../api/apiClient', () => ({ __esModule: true, default: { get: jest.fn(), post: jest.fn() }, buildUrl: p => `https://api.test/api${p}` }));
beforeEach(() => jest.clearAllMocks());
test.each([['jpg', 'image/jpeg', 5], ['jpeg', 'image/jpeg', 5], ['png', 'image/png', 5], ['pdf', 'application/pdf', 10], ['mp3', 'audio/mpeg', 20]])('aceita %s e bloqueia limite mais um', (ext, type, mb) => {
  for (const offset of [-1, 0]) expect(validateFile({ name: `obra.${ext.toUpperCase()}`, type, size: mb * 1024 * 1024 + offset })).toBe('');
  expect(validateFile({ name: `obra.${ext}`, type, size: mb * 1024 * 1024 + 1 })).toContain(`limite de ${mb} MB`);
});
test.each(['docx', 'exe', 'svg', 'gif', 'html', 'zip', 'mp4', 'mov', 'webm', 'jpg.exe', 'constructor', '__proto__'])('bloqueia %s', ext => {
  expect(validateFile({ name: `obra.${ext}`, type: '', size: 12 })).toContain('Formato não permitido');
});
test('bloqueia vazio, sem arquivo, sem extensão e MIME divergente', () => {
  expect(validateFile(null)).toBeTruthy(); expect(validateFile({ name: 'a.pdf', size: 0 })).toBeTruthy();
  expect(validateFile({ name: 'semextensao', size: 1 })).toBeTruthy();
  expect(validateFile({ name: 'a.pdf', size: 1, type: 'text/html' })).toContain('incompatível');
});
test('upload envia somente arquivo em FormData', async () => {
  const file = new File(['pdf'], 'obra.pdf', { type: 'application/pdf' }); await uploadFile(file);
  const [path, body] = apiClient.post.mock.calls[0]; expect(path).toBe('/portfolio/arquivos');
  expect(body).toBeInstanceOf(FormData); expect([...body.keys()]).toEqual(['arquivo']); expect(body.get('arquivo')).toBe(file);
});
test('lista paginada preserva token privado e remove token no público', async () => {
  await listPortfolio(true, 999, 'arquivos', 2); await listPortfolio(false, 42, 'videos', 1);
  expect(apiClient.get).toHaveBeenNthCalledWith(1, '/portfolio/me/arquivos?page=2&size=20', { signal: undefined });
  expect(apiClient.get).toHaveBeenNthCalledWith(2, '/portfolio/publico/artistas/42/videos?page=1&size=20', { token: null, signal: undefined });
});
test('vídeo envia somente link e conteúdo só usa ID seguro', async () => {
  await addVideo('https://vimeo.com/123'); expect(apiClient.post).toHaveBeenCalledWith('/portfolio/videos', { url: 'https://vimeo.com/123' });
  expect(fileContentPath('../42', true)).toBeNull(); expect(fileContentPath('https://evil.test', false)).toBeNull();
});
test.each(['javascript:alert(1)', '<iframe>', 'https://www.youtube-nocookie.com.evil.test/embed/dQw4w9WgXcQ', 'https://evil.test/video/123', 'https://player.vimeo.com/video/123?evil=1'])('rejeita iframe arbitrário %s', url => expect(safeEmbedUrl(url)).toBeNull());
test.each(['https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ', 'https://player.vimeo.com/video/123'])('aceita embed confiável %s', url => expect(safeEmbedUrl(url)).toBe(url));
