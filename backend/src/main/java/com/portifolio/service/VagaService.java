package com.portifolio.service;

import com.portifolio.dto.VagaBuscaFiltro;
import com.portifolio.dto.ContratantePublicoResponse;
import com.portifolio.dto.VagaAtualizacaoRequest;
import com.portifolio.dto.VagaCancelamentoRequest;
import com.portifolio.dto.VagaListagemResponse;
import com.portifolio.dto.VagaRequest;
import com.portifolio.dto.VagaResponse;
import com.portifolio.dto.VagaStatusAcaoRequest;
import com.portifolio.event.NotificacaoEvento;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.exception.ForbiddenException;
import com.portifolio.exception.UnprocessableEntityException;
import com.portifolio.model.Candidatura;
import com.portifolio.model.LogVagaCancelada;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Tag;
import com.portifolio.model.Usuario;
import com.portifolio.model.Vaga;
import com.portifolio.model.enums.StatusCandidatura;
import com.portifolio.model.enums.StatusVaga;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.model.enums.TipoNotificacao;
import com.portifolio.repository.CandidaturaRepository;
import com.portifolio.repository.LogVagaCanceladaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.TagRepository;
import com.portifolio.repository.VagaRepository;
import com.portifolio.repository.specification.VagaSpecifications;
import com.portifolio.security.AuthenticatedUserResolver;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VagaService {

    private static final int TAMANHO_PADRAO = 20;
    private static final int TAMANHO_MAXIMO = 50;

    private final VagaRepository vagaRepository;
    private final PerfilContratanteRepository perfilContratanteRepository;
    private final TagRepository tagRepository;
    private final CandidaturaRepository candidaturaRepository;
    private final LogVagaCanceladaRepository logVagaCanceladaRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final AvatarService avatarService;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificacaoPersistenceService notificacaoPersistenceService;

    // RF03 — Listagem e busca paginada (cursor-based) de vagas ABERTAS. Endpoint público.
    // RF03 Fase 2 — se o artista autenticado tiver candidaturas em vagas CANCELADA,
    // elas voltam em uma seção separada da resposta.
    @Transactional(readOnly = true)
    public VagaListagemResponse listar(VagaBuscaFiltro filtro) {
        validarFiltros(filtro);
        int tamanho = normalizarTamanho(filtro.getSize());
        Usuario usuarioAtual = authenticatedUserResolver.usuarioAtual().orElse(null);
        Long contratanteAtualId = contratanteAtualId(usuarioAtual);

        Specification<Vaga> spec = Specification
                .where(VagaSpecifications.comStatus(StatusVaga.ABERTA))
                .and(VagaSpecifications.idMaiorQue(filtro.getCursor()))
                .and(VagaSpecifications.tituloContem(filtro.getTitulo()))
                .and(VagaSpecifications.empresaContem(filtro.getEmpresa()))
                .and(VagaSpecifications.cidadeIgual(filtro.getCidade()))
                .and(VagaSpecifications.estadoIgual(filtro.getEstado()))
                .and(VagaSpecifications.modeloTrabalhoIgual(filtro.getModeloTrabalho()))
                .and(VagaSpecifications.tipoContratoIgual(filtro.getTipoContrato()))
                .and(VagaSpecifications.remuneracaoMinima(filtro.getFaixaSalarialMin()))
                .and(VagaSpecifications.remuneracaoMaxima(filtro.getFaixaSalarialMax()))
                .and(VagaSpecifications.areaAtuacaoContem(filtro.getAreaAtuacao()))
                .and(VagaSpecifications.comAlgumaTag(filtro.getTagIds()));

        Pageable pageable = PageRequest.of(0, tamanho + 1, Sort.by(Sort.Direction.ASC, "id"));
        List<Vaga> bruto = vagaRepository.findAll(spec, pageable).getContent();

        boolean hasMore = bruto.size() > tamanho;
        List<Vaga> pagina = hasMore ? bruto.subList(0, tamanho) : bruto;

        List<VagaResponse> content = carregarComTagsEContratante(pagina, contratanteAtualId);
        Long nextCursor = hasMore ? pagina.get(pagina.size() - 1).getId() : null;

        PaginaCanceladas canceladas = buscarVagasCanceladasParaArtistaLogado(
                usuarioAtual, filtro.getCursorCanceladas(), tamanho);

        return VagaListagemResponse.builder()
                .content(content)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .vagasCanceladasComCandidatura(canceladas.content())
                .nextCursorCanceladas(canceladas.nextCursor())
                .hasMoreCanceladas(canceladas.hasMore())
                .build();
    }

    @Transactional(readOnly = true)
    public VagaListagemResponse listarMinhas(Long cursor, Integer size) {
        Usuario usuario = exigirContratanteAtual();
        validarCursor(cursor, "Cursor");
        int tamanho = normalizarTamanho(size);

        Specification<Vaga> spec = Specification
                .where(VagaSpecifications.doContratante(usuario.getId()))
                .and(VagaSpecifications.idMaiorQue(cursor));

        Pageable pageable = PageRequest.of(0, tamanho + 1, Sort.by(Sort.Direction.ASC, "id"));
        List<Vaga> bruto = vagaRepository.findAll(spec, pageable).getContent();
        boolean hasMore = bruto.size() > tamanho;
        List<Vaga> pagina = hasMore ? bruto.subList(0, tamanho) : bruto;
        List<VagaResponse> content = carregarComTagsEContratante(pagina, usuario.getId());
        Long nextCursor = hasMore ? pagina.get(pagina.size() - 1).getId() : null;

        return VagaListagemResponse.builder()
                .content(content)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .vagasCanceladasComCandidatura(List.of())
                .build();
    }

    @Transactional(readOnly = true)
    public VagaListagemResponse listarSimilares(Long vagaId, Long cursor, Integer size) {
        validarCursor(cursor, "Cursor");
        int tamanho = normalizarTamanho(size);
        Vaga origem = vagaRepository.findById(vagaId)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));

        Set<Long> tagIds = origem.getTags().stream().map(Tag::getId).collect(Collectors.toSet());
        if (tagIds.isEmpty()) {
            return paginaVazia();
        }

        Specification<Vaga> spec = Specification
                .where(VagaSpecifications.comStatus(StatusVaga.ABERTA))
                .and(VagaSpecifications.idMaiorQue(cursor))
                .and(VagaSpecifications.idDiferente(vagaId))
                .and(VagaSpecifications.comAlgumaTag(tagIds));

        Pageable pageable = PageRequest.of(0, tamanho + 1, Sort.by(Sort.Direction.ASC, "id"));
        List<Vaga> bruto = vagaRepository.findAll(spec, pageable).getContent();
        boolean hasMore = bruto.size() > tamanho;
        List<Vaga> pagina = hasMore ? bruto.subList(0, tamanho) : bruto;
        Long contratanteAtualId = authenticatedUserResolver.usuarioAtual()
                .map(this::contratanteAtualId)
                .orElse(null);

        return VagaListagemResponse.builder()
                .content(carregarComTagsEContratante(pagina, contratanteAtualId))
                .nextCursor(hasMore ? pagina.get(pagina.size() - 1).getId() : null)
                .hasMore(hasMore)
                .vagasCanceladasComCandidatura(List.of())
                .build();
    }

    // RF03 Fase 2. O artista NUNCA é identificado por parâmetro do cliente —
    // sempre resolvido a partir do token JWT já validado pelo JwtAuthFilter (RNF08).
    private PaginaCanceladas buscarVagasCanceladasParaArtistaLogado(
            Usuario usuario, Long cursor, int tamanho) {
        if (usuario == null || usuario.getTipoUsuario() != TipoUsuario.ARTISTA) {
            return PaginaCanceladas.vazia();
        }

        Pageable limite = PageRequest.of(0, tamanho + 1);
        List<Long> bruto = candidaturaRepository.findVagaIdsDoArtistaPorStatusAposCursor(
                usuario.getId(), StatusVaga.CANCELADA, cursor, limite);
        boolean hasMore = bruto.size() > tamanho;
        List<Long> vagaIds = hasMore ? bruto.subList(0, tamanho) : bruto;
        Long nextCursor = hasMore ? vagaIds.get(vagaIds.size() - 1) : null;
        return new PaginaCanceladas(
                carregarPorIds(vagaIds, null, true), nextCursor, hasMore);
    }

    // Segunda consulta: busca tags+contratante para o conjunto de IDs já paginado (RNF05).
    // Pagination + fetch join de coleção não é seguro na mesma query.
    private List<VagaResponse> carregarComTagsEContratante(
            List<Vaga> pagina, Long contratanteAtualId) {
        if (pagina.isEmpty()) {
            return List.of();
        }
        List<Long> ids = pagina.stream().map(Vaga::getId).toList();
        return carregarPorIds(ids, contratanteAtualId, false);
    }

    private List<VagaResponse> carregarPorIds(
            List<Long> ids, Long contratanteAtualId, boolean cancelada) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Vaga> vagasComTags = vagaRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Vaga::getId, v -> v, (a, b) -> a, LinkedHashMap::new));

        return ids.stream()
                .map(vagasComTags::get)
                .map(v -> toResponse(v, cancelada, contratanteAtualId))
                .collect(Collectors.toList());
    }

    private VagaListagemResponse paginaVazia() {
        return VagaListagemResponse.builder()
                .content(List.of())
                .hasMore(false)
                .vagasCanceladasComCandidatura(List.of())
                .build();
    }

    private void validarFiltros(VagaBuscaFiltro filtro) {
        validarCursor(filtro.getCursor(), "Cursor");
        validarCursor(filtro.getCursorCanceladas(), "Cursor de vagas canceladas");
        if (filtro.getFaixaSalarialMin() != null
                && filtro.getFaixaSalarialMin().signum() < 0) {
            throw new IllegalArgumentException("Remuneração mínima não pode ser negativa.");
        }
        if (filtro.getFaixaSalarialMax() != null
                && filtro.getFaixaSalarialMax().signum() < 0) {
            throw new IllegalArgumentException("Remuneração máxima não pode ser negativa.");
        }
        if (filtro.getFaixaSalarialMin() != null && filtro.getFaixaSalarialMax() != null
                && filtro.getFaixaSalarialMin().compareTo(filtro.getFaixaSalarialMax()) > 0) {
            throw new IllegalArgumentException(
                    "Remuneração mínima não pode ser maior que a máxima.");
        }
        if (filtro.getEstado() != null && !filtro.getEstado().isBlank()
                && filtro.getEstado().trim().length() != 2) {
            throw new IllegalArgumentException("Estado deve usar exatamente 2 caracteres.");
        }
        if (filtro.getTagIds() != null
                && filtro.getTagIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("IDs de tags devem ser positivos.");
        }
    }

    private void validarCursor(Long cursor, String nome) {
        if (cursor != null && cursor < 0) {
            throw new IllegalArgumentException(nome + " não pode ser negativo.");
        }
    }

    private int normalizarTamanho(Integer solicitado) {
        if (solicitado == null || solicitado < 1) {
            return TAMANHO_PADRAO;
        }
        return Math.min(solicitado, TAMANHO_MAXIMO);
    }

    @Transactional(readOnly = true)
    public VagaResponse buscarPorId(Long id) {
        Usuario usuario = authenticatedUserResolver.usuarioAtual().orElse(null);
        Vaga vaga = vagaRepository.findDetalhesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        boolean proprietario = usuario != null
                && usuario.getTipoUsuario() == TipoUsuario.CONTRATANTE
                && vaga.getContratante().getUsuarioId().equals(usuario.getId());
        Optional<Candidatura> candidaturaDoArtista = usuario != null
                && usuario.getTipoUsuario() == TipoUsuario.ARTISTA
                ? candidaturaRepository.findByVagaIdAndArtistaUsuarioId(vaga.getId(), usuario.getId())
                : Optional.empty();

        if (vaga.getStatus() != StatusVaga.ABERTA
                && !proprietario
                && candidaturaDoArtista.isEmpty()) {
            // Não permite descobrir por ID uma vaga ausente do feed público.
            throw new ResourceNotFoundException("Vaga não encontrada.");
        }

        VagaResponse resposta = toResponse(
                vaga, false, proprietario ? usuario.getId() : null);
        Candidatura candidatura = candidaturaDoArtista.orElse(null);
        return resposta.toBuilder()
                .contratantePublico(toContratantePublico(vaga.getContratante()))
                .minhaCandidaturaId(candidatura == null ? null : candidatura.getId())
                .statusMinhaCandidatura(candidatura == null ? null : candidatura.getStatus())
                .build();
    }

    @Transactional
    public VagaResponse criar(VagaRequest request) {
        Usuario usuario = exigirContratanteAtual();
        PerfilContratante contratante = perfilContratanteRepository.findById(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Contratante não encontrado."));
        Vaga vaga = new Vaga();
        vaga.setContratante(contratante);
        preencherVaga(vaga, request);
        normalizarPublicacao(vaga);
        vaga.setStatus(StatusVaga.ABERTA);
        vaga.setDataPublicacao(LocalDateTime.now());
        return toResponse(vagaRepository.save(vaga));
    }

    @Transactional
    public VagaResponse atualizar(Long id, VagaAtualizacaoRequest request) {
        Vaga vaga = vagaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        exigirProprietario(vaga);
        StatusVaga statusAtual = vaga.getStatus();
        preencherVaga(vaga, request);
        vaga.setStatus(statusAtual);
        return toResponse(vagaRepository.save(vaga));
    }

    @Transactional
    public VagaResponse gerenciarStatus(Long id, VagaStatusAcaoRequest request) {
        Vaga vaga = vagaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        exigirProprietario(vaga);

        String acao = request.getAcao().trim().toUpperCase(Locale.ROOT);
        StatusVaga novoStatus = switch (acao) {
            case "SUSPENDER" -> exigirTransicao(
                    vaga.getStatus(), acao, StatusVaga.PAUSADA, StatusVaga.ABERTA);
            case "REABRIR" -> exigirTransicao(
                    vaga.getStatus(), acao, StatusVaga.ABERTA, StatusVaga.PAUSADA);
            case "ENCERRAR" -> exigirTransicao(
                    vaga.getStatus(), acao, StatusVaga.ENCERRADA,
                    StatusVaga.ABERTA, StatusVaga.PAUSADA);
            default -> throw new IllegalArgumentException(
                    "Ação de gerenciamento inválida: " + acao
                            + ". Ações aceitas: SUSPENDER, REABRIR e ENCERRAR.");
        };

        vaga.setStatus(novoStatus);
        Vaga salva = vagaRepository.save(vaga);
        publicarMudancaDeStatus(salva, novoStatus);
        return toResponse(salva);
    }

    @Transactional
    public void deletar(Long id, VagaCancelamentoRequest request) {
        Usuario usuario = exigirContratanteAtual();
        Vaga vaga = vagaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        if (!vaga.getContratante().getUsuarioId().equals(usuario.getId())) {
            throw new ForbiddenException("Somente o proprietário pode alterar esta vaga.");
        }
        if (vaga.getStatus() != StatusVaga.ABERTA && vaga.getStatus() != StatusVaga.PAUSADA) {
            throw new UnprocessableEntityException(
                    "A vaga no estado " + vaga.getStatus()
                            + " não pode ser cancelada. Somente vagas ABERTA ou PAUSADA podem ser canceladas.");
        }
        String motivo = validarCancelamento(request);

        vaga.setStatus(StatusVaga.CANCELADA);
        vagaRepository.save(vaga);

        List<Candidatura> candidaturas = candidaturaRepository.findByVagaId(id);
        candidaturas.forEach(c -> c.setStatus(StatusCandidatura.CANCELADA_POR_VAGA));
        candidaturaRepository.saveAll(candidaturas);

        LogVagaCancelada log = new LogVagaCancelada();
        log.setVaga(vaga);
        log.setCanceladoPor(usuario);
        log.setDataCancelamento(LocalDateTime.now());
        log.setMotivo(motivo);
        logVagaCanceladaRepository.save(log);

        publicarParaCandidatos(
                vaga,
                candidaturas,
                "A vaga \"" + vaga.getTitulo() + "\" foi cancelada.");
    }

    private void publicarMudancaDeStatus(Vaga vaga, StatusVaga novoStatus) {
        String mensagem = switch (novoStatus) {
            case PAUSADA -> "A vaga \"" + vaga.getTitulo() + "\" foi suspensa.";
            case ABERTA -> "A vaga \"" + vaga.getTitulo() + "\" foi reaberta.";
            case ENCERRADA -> "A vaga \"" + vaga.getTitulo() + "\" foi encerrada.";
            default -> null;
        };
        if (mensagem != null) {
            publicarParaCandidatos(
                    vaga, candidaturaRepository.findByVagaId(vaga.getId()), mensagem);
        }
    }

    private void publicarParaCandidatos(
            Vaga vaga, List<Candidatura> candidaturas, String mensagem) {
        Set<Long> destinatarios = candidaturas.stream()
                .map(candidatura -> candidatura.getArtista().getUsuarioId())
                .collect(Collectors.toSet());
        if (destinatarios.isEmpty()) {
            return;
        }
        NotificacaoEvento evento = new NotificacaoEvento(
                destinatarios,
                TipoNotificacao.CANDIDATURA,
                mensagem,
                "detalhe-vaga.html?id=" + vaga.getId());
        if (vaga.getStatus() == StatusVaga.CANCELADA) {
            // RF28: registros obrigatórios participam do rollback; só a entrega aguarda o commit.
            notificacaoPersistenceService.persistirNaTransacaoAtual(evento)
                    .forEach(eventPublisher::publishEvent);
        } else {
            eventPublisher.publishEvent(evento);
        }
    }

    private String validarCancelamento(VagaCancelamentoRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.getConfirmacao())) {
            throw new IllegalArgumentException("Confirmação deve ser verdadeira.");
        }
        if (request.getMotivo() == null || request.getMotivo().isBlank()) {
            throw new IllegalArgumentException("Motivo é obrigatório.");
        }
        return request.getMotivo().trim();
    }

    private void preencherVaga(Vaga vaga, VagaAtualizacaoRequest request) {
        vaga.setTitulo(request.getTitulo());
        vaga.setDescricao(request.getDescricao());
        vaga.setRequisitos(request.getRequisitos());
        vaga.setRemuneraValor(request.getRemuneraValor());
        vaga.setFormaPagamento(request.getFormaPagamento());
        vaga.setCidade(request.getCidade());
        vaga.setEstado(request.getEstado());
        vaga.setEnderecoCompleto(request.getEnderecoCompleto());
        vaga.setBeneficios(request.getBeneficios());
        vaga.setModeloTrabalho(request.getModeloTrabalho());
        vaga.setTipoContrato(request.getTipoContrato());
        vaga.setCategoria(request.getCategoria());
        vaga.setExperiencia(request.getExperiencia());
        vaga.setDataLimiteCandidatura(request.getDataLimiteCandidatura());
        vaga.setAbrangencia(request.getAbrangencia());
        if (request.getFotos() != null) {
            vaga.setFotos(new ArrayList<>(request.getFotos().stream()
                    .filter(url -> url != null && !url.isBlank())
                    .toList()));
        }
        if (request.getTagIds() != null) {
            vaga.setTags(resolverTags(request.getTagIds()));
        }
    }

    private Set<Tag> resolverTags(Set<Long> tagIds) {
        List<Tag> tags = tagRepository.findAllById(tagIds);
        if (tags.size() != tagIds.size()) {
            throw new ResourceNotFoundException("Uma ou mais tags não foram encontradas.");
        }
        return new HashSet<>(tags);
    }

    private StatusVaga exigirTransicao(
            StatusVaga atual, String acao, StatusVaga destino, StatusVaga... origensPermitidas) {
        for (StatusVaga origem : origensPermitidas) {
            if (atual == origem) {
                return destino;
            }
        }
        throw new UnprocessableEntityException(
                "Transição inválida: a ação " + acao + " não pode ser aplicada à vaga no estado "
                        + atual + ". Ações permitidas no estado atual: " + acoesPermitidas(atual) + ".");
    }

    private String acoesPermitidas(StatusVaga status) {
        return switch (status) {
            case ABERTA -> "SUSPENDER ou ENCERRAR";
            case PAUSADA -> "REABRIR ou ENCERRAR";
            case ENCERRADA, CANCELADA -> "nenhuma; este é um estado final";
        };
    }

    private void normalizarPublicacao(Vaga vaga) {
        vaga.setTitulo(vaga.getTitulo().trim());
        vaga.setDescricao(vaga.getDescricao().trim());
        vaga.setRequisitos(vaga.getRequisitos().trim());
        vaga.setFormaPagamento(vaga.getFormaPagamento().trim());
        vaga.setCidade(vaga.getCidade().trim());
        vaga.setEstado(vaga.getEstado().trim().toUpperCase(Locale.ROOT));
        vaga.setTipoContrato(vaga.getTipoContrato().trim());
        vaga.setEnderecoCompleto(normalizarOpcional(vaga.getEnderecoCompleto()));
        vaga.setBeneficios(normalizarOpcional(vaga.getBeneficios()));
        vaga.setCategoria(normalizarOpcional(vaga.getCategoria()));
        vaga.setExperiencia(normalizarOpcional(vaga.getExperiencia()));
        vaga.setAbrangencia(normalizarOpcional(vaga.getAbrangencia()));
        vaga.setFotos(new ArrayList<>(vaga.getFotos().stream()
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .toList()));
    }

    private String normalizarOpcional(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    private ContratantePublicoResponse toContratantePublico(PerfilContratante perfil) {
        Usuario usuario = perfil.getUsuario();
        String nomeEmpresa = perfil.getNomeEmpresa();
        String nomeExibicao = nomeEmpresa == null || nomeEmpresa.isBlank()
                ? usuario.getNome()
                : nomeEmpresa;
        return ContratantePublicoResponse.builder()
                .usuarioId(perfil.getUsuarioId())
                .nomeExibicao(nomeExibicao)
                .nomeEmpresa(nomeEmpresa)
                .tipoPerfil(perfil.getTipoPerfil())
                .biografia(perfil.getBiografia())
                .localizacao(perfil.getLocalizacao())
                .bannerUrl(perfil.getBannerUrl())
                .avatarUrl(avatarService.resolverUrl(
                        perfil.getUsuarioId(), usuario.getFotoPerfil(), perfil.getFotoPerfil()))
                .build();
    }

    private VagaResponse toResponse(Vaga vaga) {
        Long usuarioId = authenticatedUserResolver.usuarioAtual()
                .map(this::contratanteAtualId)
                .orElse(null);
        return toResponse(vaga, false, usuarioId);
    }

    private VagaResponse toResponse(
            Vaga vaga, boolean cancelada, Long contratanteAtualId) {
        Set<Long> tagIds = vaga.getTags().stream()
                .map(Tag::getId)
                .collect(Collectors.toSet());
        return VagaResponse.builder()
                .id(vaga.getId())
                .contratanteId(vaga.getContratante().getUsuarioId())
                .nomeContratante(vaga.getContratante().getNomeEmpresa() == null
                        || vaga.getContratante().getNomeEmpresa().isBlank()
                        ? vaga.getContratante().getUsuario().getNome()
                        : vaga.getContratante().getNomeEmpresa())
                .titulo(vaga.getTitulo())
                .descricao(vaga.getDescricao())
                .requisitos(vaga.getRequisitos())
                .remuneraValor(vaga.getRemuneraValor())
                .formaPagamento(vaga.getFormaPagamento())
                .cidade(vaga.getCidade())
                .estado(vaga.getEstado())
                .enderecoCompleto(vaga.getEnderecoCompleto())
                .beneficios(vaga.getBeneficios())
                .modeloTrabalho(vaga.getModeloTrabalho())
                .tipoContrato(vaga.getTipoContrato())
                .status(vaga.getStatus())
                .dataPublicacao(vaga.getDataPublicacao())
                .tagIds(tagIds)
                .categoria(vaga.getCategoria())
                .experiencia(vaga.getExperiencia())
                .dataLimiteCandidatura(vaga.getDataLimiteCandidatura())
                .abrangencia(vaga.getAbrangencia())
                .fotos(List.copyOf(vaga.getFotos()))
                .propriaDoContratante(contratanteAtualId != null
                        && contratanteAtualId.equals(vaga.getContratante().getUsuarioId()))
                .cancelada(cancelada)
                .build();
    }

    private Long contratanteAtualId(Usuario usuario) {
        return usuario != null && usuario.getTipoUsuario() == TipoUsuario.CONTRATANTE
                ? usuario.getId()
                : null;
    }

    private record PaginaCanceladas(
            List<VagaResponse> content, Long nextCursor, boolean hasMore) {
        private static PaginaCanceladas vazia() {
            return new PaginaCanceladas(List.of(), null, false);
        }
    }

    private Usuario exigirContratanteAtual() {
        Usuario usuario = exigirUsuarioAtual();
        if (usuario.getTipoUsuario() != TipoUsuario.CONTRATANTE) {
            throw new ForbiddenException("Somente contratantes podem gerenciar vagas.");
        }
        return usuario;
    }

    private Usuario exigirUsuarioAtual() {
        return authenticatedUserResolver.usuarioAtual()
                .orElseThrow(() -> new ForbiddenException("Autenticação obrigatória."));
    }

    private Usuario exigirProprietario(Vaga vaga) {
        Usuario usuario = exigirContratanteAtual();
        if (!vaga.getContratante().getUsuarioId().equals(usuario.getId())) {
            throw new ForbiddenException("Somente o proprietário pode alterar esta vaga.");
        }
        return usuario;
    }
}
