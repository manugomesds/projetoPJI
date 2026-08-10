package com.portifolio.service;

import com.portifolio.dto.VagaBuscaFiltro;
import com.portifolio.dto.VagaListagemResponse;
import com.portifolio.dto.VagaRequest;
import com.portifolio.dto.VagaResponse;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.exception.ForbiddenException;
import com.portifolio.model.Candidatura;
import com.portifolio.model.LogVagaCancelada;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Tag;
import com.portifolio.model.Usuario;
import com.portifolio.model.Vaga;
import com.portifolio.model.enums.StatusCandidatura;
import com.portifolio.model.enums.StatusVaga;
import com.portifolio.model.enums.TipoUsuario;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
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

    // RF03 — Listagem e busca paginada (cursor-based) de vagas ABERTAS. Endpoint público.
    // RF03 Fase 2 — se o artista autenticado tiver candidaturas em vagas CANCELADA,
    // elas voltam em uma seção separada da resposta.
    @Transactional(readOnly = true)
    public VagaListagemResponse listar(VagaBuscaFiltro filtro) {

        int tamanho = normalizarTamanho(filtro.getSize());

        Specification<Vaga> spec = Specification
                .where(VagaSpecifications.comStatus(StatusVaga.ABERTA))
                .and(VagaSpecifications.idMaiorQue(filtro.getCursor()))
                .and(VagaSpecifications.tituloContem(filtro.getTitulo()))
                .and(VagaSpecifications.cidadeIgual(filtro.getCidade()))
                .and(VagaSpecifications.estadoIgual(filtro.getEstado()))
                .and(VagaSpecifications.modeloTrabalhoIgual(filtro.getModeloTrabalho()))
                .and(VagaSpecifications.tipoContratoIgual(filtro.getTipoContrato()))
                .and(VagaSpecifications.remuneracaoMinima(filtro.getFaixaSalarialMin()))
                .and(VagaSpecifications.remuneracaoMaxima(filtro.getFaixaSalarialMax()))
                .and(VagaSpecifications.comAlgumaTag(filtro.getTagIds()));

        Pageable pageable = PageRequest.of(0, tamanho + 1, Sort.by(Sort.Direction.ASC, "id"));
        List<Vaga> bruto = vagaRepository.findAll(spec, pageable).getContent();

        boolean hasMore = bruto.size() > tamanho;
        List<Vaga> pagina = hasMore ? bruto.subList(0, tamanho) : bruto;

        List<VagaResponse> content = carregarComTagsEContratante(pagina);
        Long nextCursor = hasMore ? pagina.get(pagina.size() - 1).getId() : null;

        List<VagaResponse> vagasCanceladasComCandidatura = buscarVagasCanceladasParaArtistaLogado();

        return VagaListagemResponse.builder()
                .content(content)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .vagasCanceladasComCandidatura(vagasCanceladasComCandidatura)
                .build();
    }

    @Transactional(readOnly = true)
    public VagaListagemResponse listarMinhas(Long cursor, Integer size) {
        Usuario usuario = exigirContratanteAtual();
        int tamanho = normalizarTamanho(size);

        Specification<Vaga> spec = Specification
                .where(VagaSpecifications.doContratante(usuario.getId()))
                .and(VagaSpecifications.idMaiorQue(cursor));

        Pageable pageable = PageRequest.of(0, tamanho + 1, Sort.by(Sort.Direction.ASC, "id"));
        List<Vaga> bruto = vagaRepository.findAll(spec, pageable).getContent();
        boolean hasMore = bruto.size() > tamanho;
        List<Vaga> pagina = hasMore ? bruto.subList(0, tamanho) : bruto;
        List<VagaResponse> content = carregarComTagsEContratante(pagina);
        Long nextCursor = hasMore ? pagina.get(pagina.size() - 1).getId() : null;

        return VagaListagemResponse.builder()
                .content(content)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .vagasCanceladasComCandidatura(List.of())
                .build();
    }

    // RF03 Fase 2. O artista NUNCA é identificado por parâmetro do cliente —
    // sempre resolvido a partir do token JWT já validado pelo JwtAuthFilter (RNF08).
    private List<VagaResponse> buscarVagasCanceladasParaArtistaLogado() {
        return authenticatedUserResolver.usuarioAtual()
                .filter(usuario -> usuario.getTipoUsuario() == TipoUsuario.ARTISTA)
                .map(usuario -> {
                    List<Candidatura> candidaturas = candidaturaRepository
                            .findByArtista_UsuarioIdAndVaga_Status(usuario.getId(), StatusVaga.CANCELADA);

                    if (candidaturas.isEmpty()) {
                        return List.<VagaResponse>of();
                    }

                    List<Long> vagaIds = candidaturas.stream()
                            .map(c -> c.getVaga().getId())
                            .distinct()
                            .toList();

                    return vagaRepository.findByIdIn(vagaIds).stream()
                            .map(v -> toResponse(v, true))
                            .toList();
                })
                .orElse(List.of());
    }

    // Segunda consulta: busca tags+contratante para o conjunto de IDs já paginado (RNF05).
    // Pagination + fetch join de coleção não é seguro na mesma query.
    private List<VagaResponse> carregarComTagsEContratante(List<Vaga> pagina) {
        if (pagina.isEmpty()) {
            return List.of();
        }
        List<Long> ids = pagina.stream().map(Vaga::getId).toList();
        Map<Long, Vaga> vagasComTags = vagaRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Vaga::getId, v -> v, (a, b) -> a, LinkedHashMap::new));

        return ids.stream()
                .map(vagasComTags::get)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private int normalizarTamanho(Integer solicitado) {
        if (solicitado == null || solicitado < 1) {
            return TAMANHO_PADRAO;
        }
        return Math.min(solicitado, TAMANHO_MAXIMO);
    }

    @Transactional(readOnly = true)
    public VagaResponse buscarPorId(Long id) {
        Vaga vaga = vagaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        return toResponse(vaga);
    }

    @Transactional
    public VagaResponse criar(VagaRequest request) {
        Usuario usuario = exigirContratanteAtual();
        if (!usuario.getId().equals(request.getContratanteId())) {
            throw new ForbiddenException("Não é permitido publicar vaga para outro contratante.");
        }
        PerfilContratante contratante = perfilContratanteRepository.findById(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Contratante não encontrado."));
        Vaga vaga = new Vaga();
        vaga.setContratante(contratante);
        preencherVaga(vaga, request);
        vaga.setStatus(StatusVaga.ABERTA);
        vaga.setDataPublicacao(LocalDateTime.now());
        return toResponse(vagaRepository.save(vaga));
    }

    @Transactional
    public VagaResponse atualizar(Long id, VagaRequest request) {
        Vaga vaga = vagaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        Usuario usuario = exigirProprietario(vaga);
        if (!usuario.getId().equals(request.getContratanteId())) {
            throw new ForbiddenException("Não é permitido transferir a propriedade da vaga.");
        }
        StatusVaga statusAtual = vaga.getStatus();
        preencherVaga(vaga, request);
        vaga.setStatus(statusAtual);
        return toResponse(vagaRepository.save(vaga));
    }

    @Transactional
    public void deletar(Long id) {
        Vaga vaga = vagaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        Usuario usuario = exigirProprietario(vaga);
        if (vaga.getStatus() != StatusVaga.ABERTA && vaga.getStatus() != StatusVaga.PAUSADA) {
            throw new IllegalArgumentException("Somente vagas abertas ou pausadas podem ser canceladas.");
        }

        vaga.setStatus(StatusVaga.CANCELADA);
        vagaRepository.save(vaga);

        List<Candidatura> candidaturas = candidaturaRepository.findByVagaId(id);
        candidaturas.forEach(c -> c.setStatus(StatusCandidatura.CANCELADA_POR_VAGA));
        candidaturaRepository.saveAll(candidaturas);

        LogVagaCancelada log = new LogVagaCancelada();
        log.setVaga(vaga);
        log.setCanceladoPor(usuario);
        log.setDataCancelamento(LocalDateTime.now());
        logVagaCanceladaRepository.save(log);
    }

    private void preencherVaga(Vaga vaga, VagaRequest request) {
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

    private VagaResponse toResponse(Vaga vaga) {
        return toResponse(vaga, false);
    }

    private VagaResponse toResponse(Vaga vaga, boolean cancelada) {
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
                .cancelada(cancelada)
                .build();
    }

    private Usuario exigirContratanteAtual() {
        Usuario usuario = authenticatedUserResolver.usuarioAtual()
                .orElseThrow(() -> new ForbiddenException("Autenticação obrigatória."));
        if (usuario.getTipoUsuario() != TipoUsuario.CONTRATANTE) {
            throw new ForbiddenException("Somente contratantes podem gerenciar vagas.");
        }
        return usuario;
    }

    private Usuario exigirProprietario(Vaga vaga) {
        Usuario usuario = exigirContratanteAtual();
        if (!vaga.getContratante().getUsuarioId().equals(usuario.getId())) {
            throw new ForbiddenException("Somente o proprietário pode alterar esta vaga.");
        }
        return usuario;
    }
}
