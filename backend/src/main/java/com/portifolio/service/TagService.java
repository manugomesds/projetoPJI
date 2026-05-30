package com.portifolio.service;

import com.portifolio.dto.TagRequest;
import com.portifolio.dto.TagResponse;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.Tag;
import com.portifolio.repository.TagRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<TagResponse> listarTodos() {
        return tagRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TagResponse buscarPorId(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag não encontrada."));
        return toResponse(tag);
    }

    @Transactional
    public TagResponse criar(TagRequest request) {
        tagRepository.findByNomeIgnoreCase(request.getNome())
                .ifPresent(tag -> {
                    throw new ConflictException("Tag já cadastrada.");
                });
        Tag tag = new Tag();
        tag.setNome(request.getNome());
        return toResponse(tagRepository.save(tag));
    }

    @Transactional
    public TagResponse atualizar(Long id, TagRequest request) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag não encontrada."));
        tagRepository.findByNomeIgnoreCase(request.getNome())
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new ConflictException("Tag já cadastrada.");
                });
        tag.setNome(request.getNome());
        return toResponse(tagRepository.save(tag));
    }

    @Transactional
    public void deletar(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag não encontrada."));
        tagRepository.delete(tag);
    }

    private TagResponse toResponse(Tag tag) {
        return TagResponse.builder()
                .id(tag.getId())
                .nome(tag.getNome())
                .build();
    }
}
