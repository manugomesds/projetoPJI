import { waitFor } from '@testing-library/dom';
import fs from 'fs';
import path from 'path';

const script = fs.readFileSync(
  path.join(process.cwd(), 'public', 'js', 'main.js'),
  'utf8'
);

function montarDetalhe() {
  document.body.innerHTML = `
    <section data-vaga-carregando></section>
    <section data-vaga-erro hidden><p data-vaga-erro-texto></p></section>
    <article data-vaga-conteudo hidden>
      <p data-vaga-status></p>
      <h1 data-vaga-titulo></h1>
      <p data-vaga-empresa></p>
      <div data-vaga-resumo></div>
      <p data-vaga-descricao></p>
      <p data-vaga-requisitos></p>
      <div data-vaga-tags></div>
    </article>
  `;
  window.history.replaceState({}, '', '/detalhe-vaga.html?id=7');
  sessionStorage.clear();
  localStorage.clear();
}

test('detalhe de vaga aberta carrega anonimamente sem redirecionar ao login', async () => {
  montarDetalhe();
  window.fetch = jest.fn().mockResolvedValue({
    ok: true,
    status: 200,
    headers: { get: () => 'application/json' },
    json: async () => ({
      id: 7,
      titulo: 'Vaga pública',
      nomeContratante: 'Empresa Palco',
      cidade: 'Campinas',
      estado: 'SP',
      modeloTrabalho: 'REMOTO',
      remuneraValor: 2500,
      descricao: 'Descrição pública',
      requisitos: 'Experiência',
      status: 'ABERTA',
      funcaoIds: [3]
    })
  });

  window.eval(script);
  document.dispatchEvent(new Event('DOMContentLoaded'));

  await waitFor(() => expect(document.querySelector('[data-vaga-conteudo]')).not.toHaveAttribute('hidden'));
  expect(window.location.pathname).toBe('/detalhe-vaga.html');
  expect(window.fetch).toHaveBeenCalledWith(
    'http://localhost:8080/api/vagas/7',
    expect.objectContaining({ headers: expect.not.objectContaining({ Authorization: expect.anything() }) })
  );
  expect(document.querySelector('[data-vaga-titulo]')).toHaveTextContent('Vaga pública');
});

test('detalhe mantém o envio do JWT quando existe sessão', async () => {
  montarDetalhe();
  sessionStorage.setItem('palco.sessao', JSON.stringify({ token: 'jwt-rf05' }));
  window.fetch = jest.fn().mockResolvedValue({
    ok: true,
    status: 200,
    headers: { get: () => 'application/json' },
    json: async () => ({
      id: 7,
      titulo: 'Vaga autenticada',
      nomeContratante: 'Empresa Palco',
      cidade: 'Campinas',
      estado: 'SP',
      modeloTrabalho: 'REMOTO',
      remuneraValor: 2500,
      descricao: 'Descrição pública',
      requisitos: 'Experiência',
      status: 'ABERTA',
      funcaoIds: []
    })
  });

  window.eval(script);
  document.dispatchEvent(new Event('DOMContentLoaded'));

  await waitFor(() => expect(document.querySelector('[data-vaga-conteudo]')).not.toHaveAttribute('hidden'));
  expect(window.fetch).toHaveBeenCalledWith(
    'http://localhost:8080/api/vagas/7',
    expect.objectContaining({ headers: expect.objectContaining({ Authorization: 'Bearer jwt-rf05' }) })
  );
});
