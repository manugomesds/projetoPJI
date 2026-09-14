package com.portifolio.service;

import com.portifolio.dto.DashboardCandidaturaResponse;
import com.portifolio.dto.DashboardDisponibilidadeResponse;
import com.portifolio.dto.DashboardResponse;
import com.portifolio.dto.DashboardSecaoResponse;
import com.portifolio.dto.DashboardTalentoResponse;
import com.portifolio.dto.DashboardVagaResponse;
import com.portifolio.dto.FuncaoResponse;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Funcao;
import com.portifolio.model.Usuario;
import com.portifolio.model.Vaga;
import com.portifolio.model.enums.StatusVaga;
import com.portifolio.repository.CandidaturaRepository;
import com.portifolio.repository.MensagemChatRepository;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.VagaRepository;
import com.portifolio.repository.projection.CandidaturaDashboardProjection;
import com.portifolio.repository.projection.VagaRecomendadaProjection;
import com.portifolio.security.AuthenticatedUserResolver;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int TAMANHO_PADRAO = 5;
    private static final int TAMANHO_MAXIMO = 50;
    private static final Set<StatusVaga> STATUS_ATIVOS = Set.of(StatusVaga.ABERTA, StatusVaga.PAUSADA);
    private static final DashboardDisponibilidadeResponse NOTIFICACOES_DISPONIVEIS =
            DashboardDisponibilidadeResponse.builder()
                    .disponivel(true)
                    .mensagem("Alertas de candidaturas e mudancas nas vagas em tempo real.")
                    .build();

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final PerfilArtistaRepository perfilArtistaRepository;
    private final PerfilContratanteRepository perfilContratanteRepository;
    private final VagaRepository vagaRepository;
    private final CandidaturaRepository candidaturaRepository;
    private final MensagemChatRepository mensagemChatRepository;
    private final AvatarService avatarService;
    private final TalentoService talentoService;

    @Transactional(readOnly = true)
    public DashboardResponse buscar(Integer size) {
        int tamanho = validarTamanho(size);
        Usuario usuario = authenticatedUserResolver.usuarioAtual()
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado nao encontrado."));

        return switch (usuario.getTipoUsuario()) {
            case ARTISTA -> dashboardArtista(usuario, tamanho);
            case CONTRATANTE -> dashboardContratante(usuario, tamanho);
            default -> throw new com.portifolio.exception.ForbiddenException("Dashboard disponível para artista e contratante.");
        };
    }

    private DashboardResponse dashboardArtista(Usuario usuario, int tamanho) {
        PerfilArtista perfil = perfilArtistaRepository.buscarPublicoPorUsuarioId(usuario.getId())
                .orElse(null);
        Set<Long> funcaoIds = perfil == null
                ? Set.of()
                : perfil.getFuncoes().stream().map(Funcao::getId).collect(Collectors.toSet());

        return DashboardResponse.builder()
                .tipoUsuario(usuario.getTipoUsuario())
                .nomeExibicao(usuario.getNome())
                .avatarUrl(avatarService.resolverUrl(
                        usuario.getId(), usuario.getFotoPerfil(), null))
                .perfilCompleto(Boolean.TRUE.equals(usuario.getPerfilCompleto()))
                .notificacoes(NOTIFICACOES_DISPONIVEIS)
                .mensagens(mensagensDisponiveis(usuario.getId()))
                .vagasRecomendadas(buscarVagasRecomendadas(funcaoIds, tamanho))
                .build();
    }

    private DashboardResponse dashboardContratante(Usuario usuario, int tamanho) {
        PerfilContratante perfil = perfilContratanteRepository.buscarPublicoPorUsuarioId(usuario.getId())
                .orElse(null);
        String nomeExibicao = perfil == null || perfil.getNomeEmpresa() == null
                || perfil.getNomeEmpresa().isBlank()
                ? usuario.getNome()
                : perfil.getNomeEmpresa();

        return DashboardResponse.builder()
                .tipoUsuario(usuario.getTipoUsuario())
                .nomeExibicao(nomeExibicao)
                .avatarUrl(avatarService.resolverUrl(
                        usuario.getId(), usuario.getFotoPerfil(), null))
                .perfilCompleto(Boolean.TRUE.equals(usuario.getPerfilCompleto()))
                .notificacoes(NOTIFICACOES_DISPONIVEIS)
                .mensagens(mensagensDisponiveis(usuario.getId()))
                .candidaturasRecentes(buscarCandidaturasRecentes(usuario.getId(), tamanho))
                .talentosSugeridos(usuario.getStatusConta() == com.portifolio.model.enums.StatusConta.ATIVA
                        ? buscarTalentosSugeridos(usuario.getId(), tamanho) : secaoVazia())
                .build();
    }

    private DashboardSecaoResponse<DashboardVagaResponse> buscarVagasRecomendadas(
            Set<Long> funcaoIds, int tamanho) {
        if (funcaoIds.isEmpty()) {
            return secaoVazia();
        }

        Page<VagaRecomendadaProjection> pagina = vagaRepository.findRecomendadasPorFuncoes(
                funcaoIds, PageRequest.of(0, tamanho));
        if (pagina.isEmpty()) {
            return secaoVazia();
        }
        List<Long> ids = pagina.getContent().stream().map(VagaRecomendadaProjection::getId).toList();
        Map<Long, Vaga> vagas = vagaRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Vaga::getId, Function.identity()));
        Map<Long, Long> coincidencias = pagina.getContent().stream()
                .collect(Collectors.toMap(
                        VagaRecomendadaProjection::getId,
                        VagaRecomendadaProjection::getQuantidadeFuncoesCoincidentes));

        List<DashboardVagaResponse> content = ids.stream()
                .map(vagas::get)
                .filter(vaga -> vaga != null)
                .map(vaga -> toVagaResponse(vaga, coincidencias.get(vaga.getId())))
                .toList();
        return secao(content, pagina.getTotalElements(), pagina.hasNext());
    }

    private DashboardSecaoResponse<DashboardCandidaturaResponse> buscarCandidaturasRecentes(
            Long contratanteId, int tamanho) {
        Page<CandidaturaDashboardProjection> pagina =
                candidaturaRepository.findRecentesDoContratanteEmVagasAtivas(
                        contratanteId, STATUS_ATIVOS, PageRequest.of(0, tamanho));
        List<DashboardCandidaturaResponse> content = pagina.getContent().stream()
                .map(item -> DashboardCandidaturaResponse.builder()
                        .id(item.getId())
                        .vagaId(item.getVagaId())
                        .tituloVaga(item.getTituloVaga())
                        .artistaId(item.getArtistaId())
                        .nomeArtista(item.getNomeArtista())
                        .avatarUrl(avatarService.resolverUrl(
                                item.getArtistaId(),
                                item.getFotoPerfilUsuario(),
                                item.getFotoPerfilArtista()))
                        .status(item.getStatus())
                        .dataCandidatura(item.getDataCandidatura())
                        .build())
                .toList();
        return secao(content, pagina.getTotalElements(), pagina.hasNext());
    }

    private DashboardSecaoResponse<DashboardTalentoResponse> buscarTalentosSugeridos(Long dono, int tamanho) {
        var pagina = talentoService.recomendarDoContratante(dono, tamanho);
        var content = pagina.content().stream().map(t -> DashboardTalentoResponse.builder()
                .artistaId(t.getArtistaId()).nomeExibicao(t.getNomeExibicao())
                .biografia(t.getBiografia()).localizacao(t.getLocalizacao()).urlPortfolio(t.getUrlPortfolio())
                .avatarUrl(t.getAvatarUrl())
                .funcoes(t.getAreas().stream().flatMap(a -> a.funcoes().stream().map(f ->
                        FuncaoResponse.builder().id(f.id()).areaId(a.id()).nome(f.nome()).build()))
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .quantidadeFuncoesCoincidentes(t.getQuantidadeFuncoesCoincidentes())
                .quantidadeEspecializacoesCoincidentes(t.getQuantidadeEspecializacoesCoincidentes()).build()).toList();
        return secao(content, pagina.totalElements(), pagina.hasMore());
    }

    private DashboardVagaResponse toVagaResponse(Vaga vaga, Long coincidencias) {
        PerfilContratante contratante = vaga.getContratante();
        String nomeContratante = contratante.getNomeEmpresa() == null
                || contratante.getNomeEmpresa().isBlank()
                ? contratante.getUsuario().getNome()
                : contratante.getNomeEmpresa();
        return DashboardVagaResponse.builder()
                .id(vaga.getId())
                .titulo(vaga.getTitulo())
                .nomeContratante(nomeContratante)
                .remuneraValor(vaga.getValorMinimo() != null && vaga.getValorMinimo().equals(vaga.getValorMaximo()) ? vaga.getValorMinimo() : null)
                .cidade(vaga.getCidade())
                .estado(vaga.getEstado())
                .modeloTrabalho(vaga.getModeloTrabalho())
                .dataPublicacao(vaga.getDataPublicacao())
                .funcoes(toFuncoes(vaga.getFuncoes()))
                .quantidadeFuncoesCoincidentes(coincidencias == null ? 0 : coincidencias)
                .build();
    }

    private Set<FuncaoResponse> toFuncoes(Set<Funcao> funcoes) {
        return funcoes.stream()
                .sorted(Comparator.comparing(Funcao::getNome, String.CASE_INSENSITIVE_ORDER))
                .map(funcao -> FuncaoResponse.builder().id(funcao.getId()).areaId(funcao.getArea().getId()).nome(funcao.getNome()).build())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int validarTamanho(Integer size) {
        int tamanho = size == null ? TAMANHO_PADRAO : size;
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("size deve estar entre 1 e 50.");
        }
        return tamanho;
    }

    private DashboardDisponibilidadeResponse mensagensDisponiveis(Long usuarioId) {
        return DashboardDisponibilidadeResponse.builder()
                .disponivel(true)
                .mensagem("Converse com seus contatos profissionais com privacidade.")
                .quantidadeNaoLidas(mensagemChatRepository.countNaoLidasRecebidas(usuarioId))
                .build();
    }

    private <T> DashboardSecaoResponse<T> secao(List<T> content, long total, boolean hasMore) {
        return DashboardSecaoResponse.<T>builder()
                .content(content)
                .totalElements(total)
                .hasMore(hasMore)
                .build();
    }

    private <T> DashboardSecaoResponse<T> secaoVazia() {
        return secao(List.of(), 0, false);
    }
}
