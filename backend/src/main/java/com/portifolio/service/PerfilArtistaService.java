package com.portifolio.service;

import com.portifolio.dto.PerfilArtistaRequest;
import com.portifolio.dto.PerfilArtistaResponse;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ForbiddenException;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.Tag;
import com.portifolio.model.Usuario;
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
    private final AvatarService avatarService; // RF34
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final PerfilCompletoService perfilCompletoService;

    @Transactional(readOnly = true)
    public List<PerfilArtistaResponse> listarTodos() {
        return perfilArtistaRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PerfilArtistaResponse buscarPorId(Long id) {
        exigirArtistaAtual(id);
        PerfilArtista perfil = perfilArtistaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de artista nao encontrado."));
        return toResponse(perfil);
    }

    @Transactional
    public PerfilArtistaResponse criar(PerfilArtistaRequest request) {
        Usuario usuarioAtual = exigirArtistaAtual(request.getUsuarioId());
        if (perfilArtistaRepository.existsById(request.getUsuarioId())) {
            throw new ConflictException("Perfil de artista ja cadastrado para este usuario.");
        }
        PerfilArtista perfil = new PerfilArtista();
        perfil.setUsuario(usuarioAtual);
        preencherPerfil(perfil, request);
        perfil.setUltimaAtualizacao(LocalDateTime.now());
        PerfilArtista salvo = perfilArtistaRepository.save(perfil);
        perfilCompletoService.recalcular(usuarioAtual);
        return toResponse(salvo);
    }

    @Transactional
    public PerfilArtistaResponse atualizar(Long id, PerfilArtistaRequest request) {
        Usuario usuarioAtual = exigirArtistaAtual(id);
        validarIdDoPayload(id, request.getUsuarioId());
        PerfilArtista perfil = perfilArtistaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de artista nao encontrado."));
        preencherPerfil(perfil, request);
        perfil.setUltimaAtualizacao(LocalDateTime.now());
        PerfilArtista salvo = perfilArtistaRepository.save(perfil);
        perfilCompletoService.recalcular(usuarioAtual);
        return toResponse(salvo);
    }

    @Transactional
    public void deletar(Long id) {
        Usuario usuarioAtual = exigirArtistaAtual(id);
        PerfilArtista perfil = perfilArtistaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de artista nao encontrado."));
        perfilArtistaRepository.delete(perfil);
        perfilArtistaRepository.flush();
        perfilCompletoService.recalcular(usuarioAtual);
    }

    private Usuario exigirArtistaAtual(Long usuarioId) {
        Usuario atual = authenticatedUserResolver.usuarioAtual()
                .orElseThrow(() -> new ForbiddenException("Autenticação obrigatória."));
        if (!atual.getId().equals(usuarioId)) {
            throw new ForbiddenException("Você só pode alterar o próprio perfil.");
        }
        if (atual.getTipoUsuario() != com.portifolio.model.enums.TipoUsuario.ARTISTA) {
            throw new ForbiddenException("Somente artistas podem alterar perfil de artista.");
        }
        return atual;
    }

    private void validarIdDoPayload(Long id, Long usuarioId) {
        if (!id.equals(usuarioId)) {
            throw new ForbiddenException("O usuário do payload deve corresponder ao perfil autenticado.");
        }
    }

    private void preencherPerfil(PerfilArtista perfil, PerfilArtistaRequest request) {
        perfil.setBiografia(request.getBiografia());
        perfil.setLocalizacao(request.getLocalizacao());
        perfil.setUrlPortfolio(request.getUrlPortfolio());
        // Medalha e score pertencem a regras de engajamento e não são editáveis pelo RF08.
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
