package com.portifolio.service;

import com.portifolio.dto.VagaBuscaFiltro;
import com.portifolio.dto.VagaListagemResponse;
import com.portifolio.dto.VagaRequest;
import com.portifolio.dto.VagaResponse;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Tag;
import com.portifolio.model.Vaga;
import com.portifolio.model.enums.StatusVaga;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.TagRepository;
import com.portifolio.repository.VagaRepository;
import com.portifolio.repository.specification.VagaSpecifications;
import java.time.LocalDateTime;
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

    // RF03 — Listagem e busca paginada (cursor-based) de vagas ABERTAS. Endpoint público.
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

        // Busca tamanho+1 apenas para descobrir se existe próxima página (RNF12)
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
                .build();
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
        PerfilContratante contratante = perfilContratanteRepository.findById(request.getContratanteId())
                .orElseThrow(() -> new ResourceNotFoundException("Contratante não encontrado."));
        Vaga vaga = new Vaga();
        vaga.setContratante(contratante);
        preencherVaga(vaga, request);
        vaga.setStatus(request.getStatus() == null ? StatusVaga.ABERTA : request.getStatus());
        vaga.setDataPublicacao(LocalDateTime.now());
        return toResponse(vagaRepository.save(vaga));
    }

    @Transactional
    public VagaResponse atualizar(Long id, VagaRequest request) {
        Vaga vaga = vagaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        if (!vaga.getContratante().getUsuarioId().equals(request.getContratanteId())) {
            PerfilContratante contratante = perfilContratanteRepository.findById(request.getContratanteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Contratante não encontrado."));
            vaga.setContratante(contratante);
        }
        preencherVaga(vaga, request);
        if (request.getStatus() != null) {
            vaga.setStatus(request.getStatus());
        }
        return toResponse(vagaRepository.save(vaga));
    }

    @Transactional
    public void deletar(Long id) {
        Vaga vaga = vagaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        vagaRepository.delete(vaga);
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
        Set<Long> tagIds = vaga.getTags().stream()
                .map(Tag::getId)
                .collect(Collectors.toSet());
        return VagaResponse.builder()
                .id(vaga.getId())
                .contratanteId(vaga.getContratante().getUsuarioId())
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
                .build();
    }
}
