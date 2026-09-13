import fs from 'fs';
import path from 'path';

const publicDir = path.join(process.cwd(), 'public');

function readPublic(relativePath) {
  return fs.readFileSync(path.join(publicDir, relativePath), 'utf8');
}

test('links produtivos da gestão de vagas apontam para as rotas React', () => {
  const mainScript = readPublic(path.join('js', 'main.js'));
  expect(mainScript).toContain("['Minhas vagas', '/minhas-vagas']");
  expect(mainScript).toContain("['Publicar vaga', '/vagas/nova']");
  expect(mainScript).toContain("'/vagas/' + encodeURIComponent(vaga.id) + '/editar'");
  expect(mainScript).toContain("/vagas/' + encodeURIComponent(vaga.id) + '/gerenciar");
  expect(mainScript).toContain("window.location.href = '/minhas-vagas'");
  expect(readPublic('publicar-vaga.html')).toContain('href="/minhas-vagas"');
  expect(readPublic('detalhe-vaga-proprietario.html')).toContain('href="/minhas-vagas"');
});

test('páginas legadas da gestão permanecem disponíveis para rollback', () => {
  [
    'minhas-vagas.html',
    'publicar-vaga.html',
    'detalhe-vaga-proprietario.html',
    'editar-vagas.html',
    'editar-vagas-2.html',
    'confirmar-exclusao-vaga.html',
  ].forEach((file) => expect(fs.existsSync(path.join(publicDir, file))).toBe(true));
});
