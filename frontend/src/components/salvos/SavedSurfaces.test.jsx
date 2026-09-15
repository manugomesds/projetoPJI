import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import apiClient from '../../services/api/apiClient';
import PublicProfilePage from '../../pages/perfis/PublicProfilePage';
import VagaDetailPage from '../../pages/vagas/VagaDetailPage';
import TalentBankPage from '../../pages/talentos/TalentBankPage';
import VacancyCard from '../vagas/VacancyCard';
import { SESSION_STORAGE_KEY } from '../../auth/sessionService';
jest.mock('../../services/api/apiClient', () => ({ get: jest.fn(), post: jest.fn(), delete: jest.fn() }));
const artist = { usuarioId:73, artistaId:73, nomeExibicao:'Marina', localizacao:'São Paulo/SP',funcoes:[],areas:[] };
const vacancy = {id:91,titulo:'Festival cultural',status:'ABERTA',nomeContratante:'Casa Cultural'};
beforeEach(() => {
  jest.clearAllMocks();sessionStorage.clear();localStorage.clear();sessionStorage.setItem(SESSION_STORAGE_KEY,JSON.stringify({token:'jwt',tipoUsuario:'CONTRATANTE'}));
  apiClient.get.mockImplementation(path => {
    if(path.startsWith('/salvos/estado?')) return Promise.resolve({salvo:false,quantidadeSalvos:0});
    if(path.startsWith('/perfis/publicos/')) return Promise.resolve(artist);
    if(path.startsWith('/talentos?')) return Promise.resolve({content:[artist],page:0,size:20,totalElements:1,hasMore:false,contexto:{id:91,titulo:'Festival cultural'}});
    if(path.startsWith('/talentos/')) return Promise.resolve({content:[],page:0,totalElements:0,hasMore:false});
    if(path==='/vagas/91') return Promise.resolve(vacancy);
    return Promise.resolve([]);
  });
  apiClient.post.mockResolvedValue({salvo:true,quantidadeSalvos:1});
});
test.each(['perfil','talentos','card','card-home','detalhe'])('estrela usa contrato único na superfície %s',async surface => {
  const path=surface==='perfil'?'/perfis/ARTISTA/73':surface==='detalhe'?'/vagas/91':'/talentos';
  const view=surface==='perfil'?<PublicProfilePage/>:surface==='detalhe'?<VagaDetailPage/>:surface==='talentos'?<TalentBankPage/>:<VacancyCard vacancy={vacancy} variant={surface==='card-home'?'home':'search'}/>;
  render(<MemoryRouter initialEntries={[path]} future={{v7_relativeSplatPath:true,v7_startTransition:true}}><Routes><Route path={surface==='perfil'?'/perfis/:tipo/:id':surface==='detalhe'?'/vagas/:id':'/talentos'} element={view}/></Routes></MemoryRouter>);
  const type=['perfil','talentos'].includes(surface)?'PERFIL_ARTISTA':'VAGA';
  const buttons=await screen.findAllByRole('button',{name:type==='VAGA'?/Salvar vaga Festival/:/Salvar perfil Marina/});
  await waitFor(() => expect(buttons[0]).toBeEnabled());fireEvent.click(buttons[0]);
  expect(apiClient.post).not.toHaveBeenCalled();fireEvent.click(screen.getByRole('button',{name:'Confirmar salvamento'}));
  await waitFor(() => expect(apiClient.post).toHaveBeenCalledWith('/salvos',{tipoAlvo:type,alvoId:type==='VAGA'?91:73}));
  expect((await screen.findAllByRole('button',{name:/Remover .* dos salvos/}))[0]).toHaveAttribute('aria-pressed','true');
});
