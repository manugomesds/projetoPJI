package com.portifolio.service;

import com.portifolio.dto.PerfilArtistaRequest;
import com.portifolio.dto.PerfilArtistaResponse;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ForbiddenException;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.Tag;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.TagRepository;
import com.portifolio.security.AuthenticatedUserResolver;
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
    private final TagRepository tagRepository;
    private final AvatarService avatarService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final PerfilCompletoService perfilCompletoService;

    @Transactional(readOnly = true)
    public List<PerfilArtistaResponse> listarTodos() {
        return perfilArtistaRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PerfilArtistaResponse buscarPorId(Long id) {
        return toResponse(perfilArtistaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Perfil de artista nao encontrado.")));
    }

    @Transactional
    public PerfilArtistaResponse criar(PerfilArtistaRequest request) {
        Usuario usuario = exigirArtistaAtual();
        if (perfilArtistaRepository.existsById(usuario.getId())) {
            throw new ConflictException("Perfil de artista ja cadastrado para este usuario.");
        }
        PerfilArtista perfil = new PerfilArtista();
        perfil.setUsuario(usuario);
        preencherPreservandoOmitidos(perfil, request);
        perfil.setUltimaAtualizacao(LocalDateTime.now());
        PerfilArtista salvo = perfilArtistaRepository.save(perfil);
        perfilCompletoService.recalcular(usuario);
        return toResponse(salvo);
    }

    @Transactional
    public PerfilArtistaResponse atualizar(Long id, PerfilArtistaRequest request) {
        Usuario usuario = exigirArtistaAtual();
        exigirMesmoUsuario(id, usuario);
        PerfilArtista perfil = perfilArtistaRepository.findById(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Perfil de artista nao encontrado."));
        preencherPreservandoOmitidos(perfil, request);
        perfil.setUltimaAtualizacao(LocalDateTime.now());
        PerfilArtista salvo = perfilArtistaRepository.save(perfil);
        perfilCompletoService.recalcular(usuario);
        return toResponse(salvo);
    }

    @Transactional
    public void deletar(Long id) {
        Usuario usuario = exigirArtistaAtual();
        exigirMesmoUsuario(id, usuario);
        PerfilArtista perfil = perfilArtistaRepository.findById(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Perfil de artista nao encontrado."));
        perfilArtistaRepository.delete(perfil);
        perfilArtistaRepository.flush();
        perfilCompletoService.recalcular(usuario);
    }

    private Usuario exigirArtistaAtual() {
        Usuario usuario = authenticatedUserResolver.usuarioAtual()
                .orElseThrow(() -> new ForbiddenException("Autenticação obrigatória."));
        if (usuario.getTipoUsuario() != TipoUsuario.ARTISTA) {
            throw new ForbiddenException("Somente artistas podem alterar perfil de artista.");
        }
        return usuario;
    }

    private void exigirMesmoUsuario(Long id, Usuario usuario) {
        if (!usuario.getId().equals(id)) {
            throw new ForbiddenException("Você só pode alterar o próprio perfil.");
        }
    }

    private void preencherPreservandoOmitidos(
            PerfilArtista perfil, PerfilArtistaRequest request) {
        if (request.getBiografia() != null) {
            perfil.setBiografia(request.getBiografia());
        }
        if (request.getLocalizacao() != null) {
            perfil.setLocalizacao(request.getLocalizacao());
        }
        if (request.getUrlPortfolio() != null) {
            perfil.setUrlPortfolio(request.getUrlPortfolio());
        }
        if (request.getBannerUrl() != null) {
            perfil.setBannerUrl(request.getBannerUrl());
        }
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
        String avatarUrl = avatarService.resolverUrl(
                perfil.getUsuarioId(),
                perfil.getUsuario().getFotoPerfil(),
                perfil.getFotoPerfil());
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
