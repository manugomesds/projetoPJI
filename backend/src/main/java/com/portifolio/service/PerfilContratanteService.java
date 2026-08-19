package com.portifolio.service;

import com.portifolio.dto.PerfilContratanteRequest;
import com.portifolio.dto.PerfilContratanteResponse;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ForbiddenException;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.security.AuthenticatedUserResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PerfilContratanteService {

    private final PerfilContratanteRepository perfilContratanteRepository;
    private final AvatarService avatarService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final PerfilCompletoService perfilCompletoService;

    @Transactional(readOnly = true)
    public List<PerfilContratanteResponse> listarTodos() {
        return perfilContratanteRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PerfilContratanteResponse buscarPorId(Long id) {
        return toResponse(perfilContratanteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Perfil de contratante nao encontrado.")));
    }

    @Transactional
    public PerfilContratanteResponse criar(PerfilContratanteRequest request) {
        Usuario usuario = exigirContratanteAtual();
        if (perfilContratanteRepository.existsById(usuario.getId())) {
            throw new ConflictException(
                    "Perfil de contratante ja cadastrado para este usuario.");
        }
        PerfilContratante perfil = new PerfilContratante();
        perfil.setUsuario(usuario);
        preencherPreservandoOmitidos(perfil, request);
        PerfilContratante salvo = perfilContratanteRepository.save(perfil);
        perfilCompletoService.recalcular(usuario);
        return toResponse(salvo);
    }

    @Transactional
    public PerfilContratanteResponse atualizar(Long id, PerfilContratanteRequest request) {
        Usuario usuario = exigirContratanteAtual();
        exigirMesmoUsuario(id, usuario);
        PerfilContratante perfil = perfilContratanteRepository.findById(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Perfil de contratante nao encontrado."));
        preencherPreservandoOmitidos(perfil, request);
        PerfilContratante salvo = perfilContratanteRepository.save(perfil);
        perfilCompletoService.recalcular(usuario);
        return toResponse(salvo);
    }

    @Transactional
    public void deletar(Long id) {
        Usuario usuario = exigirContratanteAtual();
        exigirMesmoUsuario(id, usuario);
        PerfilContratante perfil = perfilContratanteRepository.findById(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Perfil de contratante nao encontrado."));
        perfilContratanteRepository.delete(perfil);
        perfilContratanteRepository.flush();
        perfilCompletoService.recalcular(usuario);
    }

    private Usuario exigirContratanteAtual() {
        Usuario usuario = authenticatedUserResolver.usuarioAtual()
                .orElseThrow(() -> new ForbiddenException("Autenticação obrigatória."));
        if (usuario.getTipoUsuario() != TipoUsuario.CONTRATANTE) {
            throw new ForbiddenException(
                    "Somente contratantes podem alterar perfil de contratante.");
        }
        return usuario;
    }

    private void exigirMesmoUsuario(Long id, Usuario usuario) {
        if (!usuario.getId().equals(id)) {
            throw new ForbiddenException("Você só pode alterar o próprio perfil.");
        }
    }

    private void preencherPreservandoOmitidos(
            PerfilContratante perfil, PerfilContratanteRequest request) {
        if (request.getNomeEmpresa() != null) {
            perfil.setNomeEmpresa(request.getNomeEmpresa());
        }
        if (request.getTipoPerfil() != null) {
            perfil.setTipoPerfil(request.getTipoPerfil());
        }
        if (request.getBiografia() != null) {
            perfil.setBiografia(request.getBiografia());
        }
        if (request.getLocalizacao() != null) {
            perfil.setLocalizacao(request.getLocalizacao());
        }
        if (request.getBannerUrl() != null) {
            perfil.setBannerUrl(request.getBannerUrl());
        }
    }

    private PerfilContratanteResponse toResponse(PerfilContratante perfil) {
        String avatarUrl = avatarService.resolverUrl(
                perfil.getUsuarioId(),
                perfil.getUsuario().getFotoPerfil(),
                perfil.getFotoPerfil());
        return PerfilContratanteResponse.builder()
                .usuarioId(perfil.getUsuarioId())
                .nomeEmpresa(perfil.getNomeEmpresa())
                .tipoPerfil(perfil.getTipoPerfil())
                .biografia(perfil.getBiografia())
                .localizacao(perfil.getLocalizacao())
                .bannerUrl(perfil.getBannerUrl())
                .avatarUrl(avatarUrl)
                .build();
    }
}
