package com.portifolio.service;

import com.portifolio.dto.*;
import com.portifolio.dto.TalentoResponse.*;
import com.portifolio.exception.*;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.*;
import com.portifolio.repository.*;
import com.portifolio.security.AuthenticatedUserResolver;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TalentoService {
    private final TalentoRepository talentos;
    private final AreaArtisticaRepository areas;
    private final FuncaoRepository funcoes;
    private final EspecializacaoRepository especializacoes;
    private final AuthenticatedUserResolver autenticado;
    private final AvatarService avatars;

    private Usuario contratante() {
        Usuario u = autenticado.usuarioAtual().orElseThrow(() -> new UnauthorizedException("Autenticação necessária."));
        if (u.getTipoUsuario() != TipoUsuario.CONTRATANTE || u.getStatusConta() != StatusConta.ATIVA)
            throw new ForbiddenException("Banco de Talentos exclusivo para contratantes com conta ativa.");
        return u;
    }

    public Pagina<TalentoResponse> buscar(FiltroTalentos filtro) {
        return buscarDoContratante(contratante().getId(), filtro);
    }

    // Chamada interna do dashboard; o dono é resolvido do JWT pelo DashboardService.
    public Pagina<TalentoResponse> recomendarDoContratante(Long dono, int size) {
        return buscarDoContratante(dono, FiltroTalentos.recomendados(size));
    }

    private Pagina<TalentoResponse> buscarDoContratante(Long dono, FiltroTalentos f) {
        var contexto = f.vagaId() != null || f.recomendados() ? talentos.contexto(dono, f.vagaId()) : null;
        Short area = f.areaId();
        if (contexto != null) {
            if (area != null && !area.equals(contexto.dados().areaId()))
                throw new UnprocessableEntityException("Área incompatível com a vaga de contexto.");
            area = contexto.dados().areaId();
            validarTaxonomia(area, contexto.funcoes(), contexto.especializacoes());
        }
        validarTaxonomia(area, f.funcaoIds(), f.especializacaoIds());
        if (f.experienciaMinima() != null && f.experienciaMinima() != NivelExperiencia.SEM_EXPERIENCIA && area == null)
            throw new UnprocessableEntityException("Selecione a área para filtrar a experiência.");
        if (f.recomendados() && contexto == null)
            return new Pagina<>(List.of(), f.page(), f.size(), 0, false, null);
        var pagina = talentos.buscar(f, area, contexto);
        var content = pagina.content().stream().map(t -> TalentoResponse.builder()
                .artistaId(t.getArtistaId()).nomeExibicao(t.getNomeExibicao())
                .avatarUrl(avatars.resolverUrl(t.getArtistaId(), t.getAvatarUrl(), null))
                .biografia(t.getBiografia()).localizacao(t.getLocalizacao()).urlPortfolio(t.getUrlPortfolio())
                .tipoPerfilArtistico(t.getTipoPerfilArtistico()).raioAtuacao(t.getRaioAtuacao())
                .disponivelOportunidades(t.getDisponivelOportunidades()).areas(t.getAreas())
                .quantidadeFuncoesCoincidentes(t.getQuantidadeFuncoesCoincidentes())
                .quantidadeEspecializacoesCoincidentes(t.getQuantidadeEspecializacoesCoincidentes())
                .ultimaAtualizacao(t.getUltimaAtualizacao()).build()).toList();
        return new Pagina<>(content, pagina.page(), pagina.size(), pagina.totalElements(), pagina.hasMore(), pagina.contexto());
    }

    private void validarTaxonomia(Short area, Set<Long> funcs, Set<Long> specs) {
        if (area != null && !areas.existsById(area)) throw new UnprocessableEntityException("Área inexistente.");
        if ((!funcs.isEmpty() || !specs.isEmpty()) && area == null)
            throw new UnprocessableEntityException("Selecione uma área para funções e especializações.");
        if (funcs.size() > 50 || specs.size() > 50) throw new UnprocessableEntityException("Taxonomia excede o limite de 50 opções.");
        if (!funcs.isEmpty() && funcoes.countByAreaIdAndIdIn(area, funcs) != funcs.size())
            throw new UnprocessableEntityException("Função inexistente ou incompatível com a área.");
        if (!specs.isEmpty() && (funcs.isEmpty() || especializacoes.contarCompativeis(area, funcs, specs) != specs.size()))
            throw new UnprocessableEntityException("Especialização inexistente ou incompatível com as funções.");
    }

    public Pagina<Contexto> contextos(int page, int size) {
        var u = contratante(); validarPagina(page, size);
        return talentos.contextos(u.getId(), page, size);
    }

    public Pagina<Item> areas(int page, int size) {
        contratante(); validarPagina(page, size);
        return pagina(areas.findAll(PageRequest.of(page, size, Sort.by("id")))
                .map(a -> new Item(a.getId().longValue(), a.getNome())));
    }

    public Pagina<Item> funcoes(Short areaId, int page, int size) {
        contratante(); validarPagina(page, size);
        if (areaId == null) throw new UnprocessableEntityException("Selecione uma área.");
        validarTaxonomia(areaId, Set.of(), Set.of());
        return pagina(funcoes.findByAreaId(areaId, PageRequest.of(page, size, Sort.by("id")))
                .map(f -> new Item(f.getId(), f.getNome())));
    }

    public Pagina<Item> especializacoes(Short areaId, Set<Long> funcaoIds, int page, int size) {
        contratante(); validarPagina(page, size);
        var ids = funcaoIds == null ? Set.<Long>of() : funcaoIds;
        validarTaxonomia(areaId, ids, Set.of());
        if (areaId == null || ids.isEmpty()) throw new UnprocessableEntityException("Selecione área e funções.");
        return pagina(especializacoes.buscarCompativeis(areaId, ids, PageRequest.of(page, size))
                .map(e -> new Item(e.getId(), e.getNome())));
    }

    private void validarPagina(int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("page deve ser não negativo e size entre 1 e 50.");
    }
    private <T> Pagina<T> pagina(Page<T> p) {
        return new Pagina<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.hasNext(), null);
    }
}
