import fs from 'fs';
import path from 'path';
import { fireEvent, waitFor } from '@testing-library/dom';

const script = fs.readFileSync(
  path.join(process.cwd(), 'public', 'js', 'perfil-publico.js'),
  'utf8'
);

function montarPagina() {
  document.body.innerHTML = `
    <section data-estado-carregando></section>
    <section data-estado-erro hidden><h1 data-erro-titulo></h1><p data-erro-mensagem></p></section>
    <article data-perfil hidden>
      <img data-banner hidden><img data-avatar>
      <p data-tipo></p><h1 data-nome></h1><p data-localizacao hidden></p>
      <div data-tags hidden></div>
      <button id="aba-sobre" data-aba="sobre" aria-selected="true"></button>
      <button id="aba-portfolio" data-aba="portfolio" aria-selected="false" hidden></button>
      <section data-painel="sobre"><p data-biografia></p><dl data-detalhes-contratante hidden><dd data-tipo-perfil></dd></dl></section>
      <section data-painel="portfolio" hidden><a data-portfolio></a></section>
    </article>`;
}

function iniciar(url) {
  window.history.replaceState(null, '', url);
  window.eval(script);
  document.dispatchEvent(new Event('DOMContentLoaded'));
}

beforeEach(() => {
  window.fetch = jest.fn();
  montarPagina();
});

test('URL direta carrega artista sem JWT e renderiza somente dados públicos', async () => {
  window.fetch.mockResolvedValue({
    ok: true,
    json: async () => ({
      usuarioId: 12,
      nomeExibicao: '<img src=x onerror=alert(1)>',
      biografia: '<script>perigo()</script>',
      localizacao: 'Campinas - SP',
      avatarUrl: 'https://cdn.example/avatar.jpg',
      bannerUrl: 'https://cdn.example/banner.jpg',
      urlPortfolio: 'https://portfolio.example/pessoa',
      funcoes: [{ id: 1, nome: '<b>Teatro</b>' }]
    })
  });

  iniciar('/perfil-publico.html?tipo=ARTISTA&id=12');

  await waitFor(() => expect(document.querySelector('[data-perfil]')).toBeVisible());
  expect(window.fetch).toHaveBeenCalledWith(
    'http://localhost:8080/api/perfis/publicos/ARTISTA/12',
    { headers: { Accept: 'application/json' } }
  );
  expect(document.querySelector('[data-nome]')).toHaveTextContent('<img src=x onerror=alert(1)>');
  expect(document.querySelector('[data-biografia]')).toHaveTextContent('<script>perigo()</script>');
  expect(document.querySelector('[data-tags]')).toHaveTextContent('<b>Teatro</b>');
  expect(document.querySelector('[data-nome] img')).toBeNull();
  expect(document.body.textContent).not.toMatch(/email|telefone|nascimento|senha/i);
});

test('aba Portfólio troca o painel sem navegar ou recarregar', async () => {
  window.fetch.mockResolvedValue({
    ok: true,
    json: async () => ({
      nomeExibicao: 'Artista',
      avatarUrl: 'https://cdn.example/avatar.jpg',
      urlPortfolio: 'https://portfolio.example/artista',
      funcoes: []
    })
  });
  iniciar('/perfil-publico.html?tipo=ARTISTA&id=7');
  await waitFor(() => expect(document.querySelector('[data-aba="portfolio"]')).toBeVisible());
  const urlAntes = window.location.href;

  fireEvent.click(document.querySelector('[data-aba="portfolio"]'));

  expect(document.querySelector('[data-painel="sobre"]')).not.toBeVisible();
  expect(document.querySelector('[data-painel="portfolio"]')).toBeVisible();
  expect(document.querySelector('[data-aba="portfolio"]')).toHaveAttribute('aria-selected', 'true');
  expect(window.location.href).toBe(urlAntes);
  expect(window.fetch).toHaveBeenCalledTimes(1);
});

test('contratante renderiza seu tipo sem aba de portfólio', async () => {
  window.fetch.mockResolvedValue({
    ok: true,
    json: async () => ({ nomeExibicao: 'Produtora Palco', tipoPerfil: 'Produtora', avatarUrl: 'https://cdn.example/a.jpg' })
  });
  iniciar('/perfil-publico.html?tipo=CONTRATANTE&id=4');

  await waitFor(() => expect(document.querySelector('[data-perfil]')).toBeVisible());
  expect(document.querySelector('[data-tipo-perfil]')).toHaveTextContent('Produtora');
  expect(document.querySelector('[data-detalhes-contratante]')).toBeVisible();
  expect(document.querySelector('[data-aba="portfolio"]')).not.toBeVisible();
});

test('perfil inexistente mostra estado seguro sem dados pessoais', async () => {
  window.fetch.mockResolvedValue({ ok: false, status: 404 });
  iniciar('/perfil-publico.html?tipo=ARTISTA&id=999');

  await waitFor(() => expect(document.querySelector('[data-estado-erro]')).toBeVisible());
  expect(document.querySelector('[data-erro-titulo]')).toHaveTextContent('Perfil não encontrado');
  expect(document.querySelector('[data-perfil]')).not.toBeVisible();
});

test('parâmetros inválidos são bloqueados antes da API', async () => {
  iniciar('/perfil-publico.html?tipo=ADMIN&id=abc');

  await waitFor(() => expect(document.querySelector('[data-estado-erro]')).toBeVisible());
  expect(window.fetch).not.toHaveBeenCalled();
  expect(document.querySelector('[data-erro-titulo]')).toHaveTextContent('Endereço de perfil inválido');
});

test('script evita sinks HTML e rejeita links com protocolo perigoso', async () => {
  expect(script).not.toMatch(/\.innerHTML\s*=|insertAdjacentHTML|document\.write/);
  window.fetch.mockResolvedValue({
    ok: true,
    json: async () => ({ nomeExibicao: 'Artista', avatarUrl: 'javascript:alert(1)', urlPortfolio: 'data:text/html,x', funcoes: [] })
  });
  iniciar('/perfil-publico.html?tipo=ARTISTA&id=5');

  await waitFor(() => expect(document.querySelector('[data-perfil]')).toBeVisible());
  expect(document.querySelector('[data-avatar]')).not.toHaveAttribute('src');
  expect(document.querySelector('[data-aba="portfolio"]')).not.toBeVisible();
});
