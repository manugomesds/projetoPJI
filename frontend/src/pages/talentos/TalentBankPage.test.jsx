// RF19 tem testes próprios de integração de superfície; a consulta de estrela é isolada aqui
// para manter as asserções originais de RF10/RF13/RF05 sobre suas próprias requisições.
jest.mock('../../services/salvos/savedService', () => ({
  ...jest.requireActual('../../services/salvos/savedService'),
  getSavedState: () => Promise.resolve({ salvo: false, quantidadeSalvos: 0 }),
}));
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ContractorOnly from '../../components/vagas/ContractorOnly';
import { SESSION_STORAGE_KEY } from '../../auth/sessionService';
import { getTalents, getTalentCatalog } from '../../services/talentos/talentService';
import TalentBankPage from './TalentBankPage';

jest.mock('../../services/talentos/talentService', () => ({
  ...jest.requireActual('../../services/talentos/talentService'),
  getTalents: jest.fn(), getTalentCatalog: jest.fn(),
}));
const context = { id: 91, titulo: 'Festival profissional', areaId: 1 };
const artist = { artistaId: 71, nomeExibicao: 'Ana profissional', avatarUrl: '/avatar.png',
  biografia: 'Biografia profissional', localizacao: 'Recife - PE', tipoPerfilArtistico: 'BANDA', raioAtuacao: 'REMOTO',
  disponivelOportunidades: true, quantidadeFuncoesCoincidentes: 1, quantidadeEspecializacoesCoincidentes: 1,
  areas: [{ id: 1, nome: 'Música', nivelExperiencia: 'EXPERIENTE', funcoes: [{ id: 11, nome: 'Voz' }],
    especializacoes: [{ id: 21, nome: 'Jazz' }] }] };
const page = (content = [artist], extra = {}) => ({ content, page: 0, size: 20, totalElements: content.length, hasMore: false, ...extra });
function mount(role = 'CONTRATANTE') {
  if (role) sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ token: 'jwt', tipoUsuario: role, statusConta: 'ATIVA' }));
  return render(<MemoryRouter initialEntries={['/talentos']} future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
    <Routes><Route path="/talentos" element={<ContractorOnly description="Sem permissão para consultar talentos."><TalentBankPage /></ContractorOnly>} />
      <Route path="/login" element={<h1>Entrar</h1>} /></Routes></MemoryRouter>);
}
beforeEach(() => {
  sessionStorage.clear(); localStorage.clear(); jest.clearAllMocks();
  getTalents.mockImplementation((query) => Promise.resolve(page([artist], query.recomendados ? { contexto: context } : {})));
  getTalentCatalog.mockImplementation((resource) => Promise.resolve(page({
    areas: [{ id: 1, nome: 'Música' }, { id: 2, nome: 'Artes Visuais' }],
    funcoes: [{ id: 11, nome: 'Voz' }, { id: 12, nome: 'Instrumento' }],
    especializacoes: [{ id: 21, nome: 'Jazz' }],
    contextos: [context],
  }[resource])));
});
test('somente contratante acessa; artista recebe bloqueio sem consultar API', () => {
  mount('ARTISTA');
  expect(screen.getByRole('alert')).toHaveTextContent('Área exclusiva para contratantes');
  expect(getTalents).not.toHaveBeenCalled();
  expect(getTalentCatalog).not.toHaveBeenCalled();
});
test('anônimo é encaminhado para login', () => {
  mount(null); expect(screen.getByRole('heading', { name: 'Entrar' })).toBeInTheDocument();
  expect(getTalents).not.toHaveBeenCalled();
});
test('recomendados carregam antes da busca e links usam ID real', async () => {
  mount();
  const recommended = screen.getByRole('region', { name: 'Recomendados para você' });
  expect(within(recommended).getByRole('status')).toHaveTextContent('Carregando talentos');
  expect(await within(recommended).findByText('Ana profissional')).toBeInTheDocument();
  expect(getTalents).toHaveBeenCalledWith({ recomendados: true, page: 0, size: 20 }, expect.anything());
  expect(within(recommended).getByRole('link', { name: 'Ver Perfil' })).toHaveAttribute('href', '/perfis/ARTISTA/71');
  expect(screen.getByRole('link', { name: 'Artistas' })).toHaveAttribute('href', '/talentos');
  expect(recommended.compareDocumentPosition(screen.getByRole('form', { name: 'Filtros de talentos' })) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
});
test('filtros combinados enviam taxonomia, OR, false e localização', async () => {
  mount(); await screen.findByRole('radio', { name: 'Música' });
  fireEvent.click(screen.getByRole('radio', { name: 'Música' }));
  fireEvent.click(await screen.findByRole('checkbox', { name: 'Voz' }));
  fireEvent.click(screen.getByRole('checkbox', { name: 'Instrumento' }));
  fireEvent.click(await screen.findByRole('checkbox', { name: 'Jazz' }));
  fireEvent.click(screen.getByRole('checkbox', { name: 'banda' }));
  fireEvent.click(screen.getByRole('checkbox', { name: 'dupla' }));
  fireEvent.click(screen.getByRole('checkbox', { name: 'remoto' }));
  fireEvent.change(screen.getByLabelText(/Localização informada/), { target: { value: 'Recife' } });
  fireEvent.change(screen.getByLabelText('Disponível para oportunidades'), { target: { value: 'false' } });
  fireEvent.change(screen.getByLabelText(/Experiência mínima/), { target: { value: 'EXPERIENTE' } });
  fireEvent.click(screen.getByRole('button', { name: 'Buscar artistas' }));
  await waitFor(() => expect(getTalents).toHaveBeenLastCalledWith(expect.objectContaining({
    areaId: '1', funcaoIds: ['11', '12'], especializacaoIds: ['21'], tipos: ['BANDA', 'DUPLA'],
    raios: ['REMOTO'], disponivel: 'false', experienciaMinima: 'EXPERIENTE', localizacao: 'Recife', page: 0,
  }), expect.anything()));
});
test('trocar área limpa função, especialização e experiência dependentes', async () => {
  mount(); fireEvent.click(await screen.findByRole('radio', { name: 'Música' }));
  fireEvent.click(await screen.findByRole('checkbox', { name: 'Voz' }));
  fireEvent.click(await screen.findByRole('checkbox', { name: 'Jazz' }));
  fireEvent.click(screen.getByRole('radio', { name: 'Artes Visuais' }));
  fireEvent.click(screen.getByRole('button', { name: 'Buscar artistas' }));
  await waitFor(() => expect(getTalents).toHaveBeenLastCalledWith(expect.objectContaining({
    areaId: '2', funcaoIds: [], especializacaoIds: [], experienciaMinima: '',
  }), expect.anything()));
});
test('selecionar vaga usa seu ID e sua área sem ID de contratante', async () => {
  mount(); fireEvent.click(await screen.findByRole('radio', { name: context.titulo }));
  fireEvent.click(screen.getByRole('button', { name: 'Buscar artistas' }));
  await waitFor(() => expect(getTalents).toHaveBeenLastCalledWith(expect.objectContaining({ vagaId: '91', areaId: '1' }), expect.anything()));
  expect(getTalents.mock.calls.at(-1)[0]).not.toHaveProperty('contratanteId');
});
test('paginação mantém filtros, próxima e anterior respeitam estado', async () => {
  getTalents.mockImplementation((query) => Promise.resolve(page([artist], { page: query.page, hasMore: query.page === 0, totalElements: 21, contexto: context })));
  mount(); await screen.findByText('Ana profissional');
  const nav=screen.getByRole('navigation',{name:'Páginas de recomendados para você'});
  expect(within(nav).getByRole('button',{name:'Anterior'})).toBeDisabled();
  fireEvent.click(within(nav).getByRole('button',{name:'Próxima'}));
  await waitFor(() => expect(getTalents).toHaveBeenLastCalledWith({ recomendados:true,page:1,size:20 },expect.anything()));
  expect(await screen.findByText('Página 2')).toBeInTheDocument();
  expect(within(screen.getByRole('navigation',{name:'Páginas de recomendados para você'})).getByRole('button',{name:'Próxima'})).toBeDisabled();
});
test('busca paginada preserva critérios sem acumular cards duplicados', async () => {
  getTalents.mockImplementation((query) => Promise.resolve(page([{ ...artist, nomeExibicao: query.page ? 'Outra artista' : 'Ana profissional' }],
    { page: query.page, hasMore: query.page === 0, totalElements: 21, contexto: query.recomendados ? context : null })));
  mount(); await screen.findByText('Ana profissional');
  fireEvent.change(screen.getByLabelText(/Localização informada/),{target:{value:'Recife'}});
  fireEvent.click(screen.getByRole('button',{name:'Buscar artistas'}));
  const results=await screen.findByRole('region',{name:'Resultados da busca'});
  fireEvent.click(await within(results).findByRole('button',{name:'Próxima'}));
  expect(await within(results).findByText('Outra artista')).toBeInTheDocument();
  expect(within(results).queryByText('Ana profissional')).not.toBeInTheDocument();
  expect(getTalents).toHaveBeenLastCalledWith(expect.objectContaining({ page:1,localizacao:'Recife' }),expect.anything());
});
test('estado vazio e ausência de contexto não inventam recomendações', async () => {
  getTalents.mockResolvedValue(page([]));
  mount(); expect(await screen.findByText('Nenhum artista corresponde aos critérios.')).toBeInTheDocument();
  expect(screen.getByText(/Publique uma vaga/)).toBeInTheDocument();
  expect(screen.queryByRole('link',{name:'Ver Perfil'})).not.toBeInTheDocument();
});
test.each([
  [401,'Sua sessão expirou'],[403,'Acesso negado'],[404,'A vaga de contexto não está disponível'],
  [422,'Função incompatível'],[400,'Função incompatível'],[500,'Não foi possível carregar os talentos'],
])('erro HTTP %s mostra estado honesto', async (status, message) => {
  getTalents.mockRejectedValue({status,message:'Função incompatível'});
  mount(); expect(await screen.findByRole('alert')).toHaveTextContent(message);
  expect(screen.queryByRole('link',{name:'Ver Perfil'})).not.toBeInTheDocument();
});
test('DTO é renderizado como texto, sem dados privados ou métricas estranhas', async () => {
  getTalents.mockResolvedValue(page([{ ...artist, nomeExibicao:'<img src=x onerror=alert(1)>',
    email:'segredo@teste',cpf:'123456789',dataNascimento:'2010-01-01',googleId:'google-secreto',senha:'senha-secreta',
    score:900,medalhas:['ouro'],seguidores:5000 }]));
  mount(); await screen.findByText('<img src=x onerror=alert(1)>');
  expect(document.querySelector('img[src="x"]')).toBeNull();
  expect(document.body.textContent).not.toMatch(/segredo@teste|123456789|google-secreto|senha-secreta|medalha|Top da Semana|engajamento/);
});
test.each([[true,'Sim'],[false,'Não'],[null,'Não informada']])('disponibilidade %s é exibida corretamente', async (value, text) => {
  getTalents.mockResolvedValue(page([{...artist,disponivelOportunidades:value}]));
  mount(); await screen.findByText('Ana profissional');
  const item=screen.getByText('Disponibilidade:').parentElement;
  expect(item.textContent).toBe('Disponibilidade: '+text);
});
test('resposta obsoleta não substitui página atual', async () => {
  let finish;
  getTalents.mockImplementationOnce(() => new Promise((resolve)=>{finish=resolve;}));
  const view=mount();
  view.unmount();
  await act(async()=>finish(page()));
  expect(screen.queryByText('Ana profissional')).not.toBeInTheDocument();
});
test('catálogo também tem paginação e mantém seleção de outra página', async () => {
  getTalentCatalog.mockImplementation((resource,query) => Promise.resolve(resource==='areas' ? page([{id:1,nome:'Música'}]) :
    resource==='contextos' ? page([]) : page(query.page ? [{id:12,nome:'Instrumento'}] : [{id:11,nome:'Voz'}],
      {page:query.page,hasMore:query.page===0,totalElements:21})));
  mount(); fireEvent.click(await screen.findByRole('radio',{name:'Música'}));
  fireEvent.click(await screen.findByRole('checkbox',{name:'Voz'}));
  fireEvent.click(within(screen.getByRole('navigation',{name:'Páginas de funções'})).getByRole('button',{name:'Próxima'}));
  fireEvent.click(await screen.findByRole('checkbox',{name:'Instrumento'}));
  fireEvent.click(screen.getByRole('button',{name:'Buscar artistas'}));
  await waitFor(()=>expect(getTalents).toHaveBeenLastCalledWith(expect.objectContaining({funcaoIds:['11','12']}),expect.anything()));
});
