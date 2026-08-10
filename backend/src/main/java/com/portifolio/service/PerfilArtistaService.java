package com.portifolio.service;

import com.portifolio.dto.PerfilArtistaRequest;
import com.portifolio.dto.PerfilArtistaResponse;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.Tag;
import com.portifolio.model.Usuario;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.TagRepository;
import com.portifolio.repository.UsuarioRepository;
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
public class PerfilArtistaService {

    private final PerfilArtistaRepository perfilArtistaRepository;
    private final UsuarioRepository usuarioRepository;
    private final TagRepository tagRepository;
    private final AvatarService avatarService; // RF34

    @Transactional(readOnly = true)
    public List<PerfilArtistaResponse> listarTodos() {
        return perfilArtistaRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PerfilArtistaResponse buscarPorId(Long id) {
        PerfilArtista perfil = perfilArtistaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de artista nao encontrado."));
        return toResponse(perfil);
    }

    @Transactional
    public PerfilArtistaResponse criar(PerfilArtistaRequest request) {
        if (perfilArtistaRepository.existsById(request.getUsuarioId())) {
            throw new ConflictException("Perfil de artista ja cadastrado para este usuario.");
        }
        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado."));
        PerfilArtista perfil = new PerfilArtista();
        perfil.setUsuario(usuario);
        preencherPerfil(perfil, request);
        perfil.setUltimaAtualizacao(LocalDateTime.now());
        return toResponse(perfilArtistaRepository.save(perfil));
    }

    @Transactional
    public PerfilArtistaResponse atualizar(Long id, PerfilArtistaRequest request) {
        PerfilArtista perfil = perfilArtistaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de artista nao encontrado."));
        preencherPerfil(perfil, request);
        perfil.setUltimaAtualizacao(LocalDateTime.now());
        return toResponse(perfilArtistaRepository.save(perfil));
    }

    @Transactional
    public void deletar(Long id) {
        PerfilArtista perfil = perfilArtistaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de artista nao encontrado."));
        perfilArtistaRepository.delete(perfil);
    }

    private void preencherPerfil(PerfilArtista perfil, PerfilArtistaRequest request) {
        perfil.setBiografia(request.getBiografia());
        perfil.setLocalizacao(request.getLocalizacao());
        perfil.setUrlPortfolio(request.getUrlPortfolio());
        perfil.setNivelMedalha(request.getNivelMedalha());
        perfil.setScoreEngajamento(request.getScoreEngajamento());
        perfil.setBannerUrl(request.getBannerUrl());
        if (request.getTagIds() != null) {
            perfil.setTags(resolverTags(request.getTagIds()));
        }
    }

    private Set<Tag> resolverTags(Set<Long> tagIds) {
        List<Tag> tags = tagRepository.findAllById(tagIds);
        if (tags.size() != tagIds.size()) {
            throw new ResourceNotFoundException("Uma ou mais tags nao foram encontradas.");
        }
        return new HashSet<>(tags);
    }

    private PerfilArtistaResponse toResponse(PerfilArtista perfil) {
        Set<Long> tagIds = perfil.getTags().stream()
                .map(Tag::getId)
                .collect(Collectors.toSet());

        // RF34: prioridade foto_perfil do perfil > foto do Google (usuarios.foto_perfil) > DiceBear
        String avatarUrl = avatarService.resolverUrl(
                perfil.getUsuarioId(),
                perfil.getUsuario().getFotoPerfil(),  // foto salva via Google (RF32)
                perfil.getFotoPerfil()                // foto definida pelo usuario via RF08
        );

        return PerfilArtistaResponse.builder()
                .usuarioId(perfil.getUsuarioId())
                .biografia(perfil.getBiografia())
                .localizacao(perfil.getLocalizacao())
                .urlPortfolio(perfil.getUrlPortfolio())
                .nivelMedalha(perfil.getNivelMedalha())
                .scoreEngajamento(perfil.getScoreEngajamento())
                .bannerUrl(perfil.getBannerUrl())
                .ultimaAtualizacao(perfil.getUltimaAtualizacao())
                .tagIds(tagIds)
                .avatarUrl(avatarUrl)
                .build();
    }
}