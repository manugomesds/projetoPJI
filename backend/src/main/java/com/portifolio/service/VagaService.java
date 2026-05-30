package com.portifolio.service;

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
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VagaService {

    private final VagaRepository vagaRepository;
    private final PerfilContratanteRepository perfilContratanteRepository;
    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<VagaResponse> listarTodos() {
        return vagaRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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
