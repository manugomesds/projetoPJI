package com.portifolio.service;

import com.portifolio.dto.PerfilContratanteRequest;
import com.portifolio.dto.PerfilContratanteResponse;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.exception.ForbiddenException;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Usuario;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.security.AuthenticatedUserResolver;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PerfilContratanteService {

    private final PerfilContratanteRepository perfilContratanteRepository;
    private final UsuarioRepository usuarioRepository;
    private final AvatarService avatarService; // RF34
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Transactional(readOnly = true)
    public List<PerfilContratanteResponse> listarTodos() {
        return perfilContratanteRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PerfilContratanteResponse buscarPorId(Long id) {
        PerfilContratante perfil = perfilContratanteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de contratante nao encontrado."));
        return toResponse(perfil);
    }

    @Transactional
    public PerfilContratanteResponse criar(PerfilContratanteRequest request) {
        exigirProprioUsuario(request.getUsuarioId());
        if (perfilContratanteRepository.existsById(request.getUsuarioId())) {
            throw new ConflictException("Perfil de contratante ja cadastrado para este usuario.");
        }
        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado."));
        PerfilContratante perfil = new PerfilContratante();
        perfil.setUsuario(usuario);
        preencherPerfil(perfil, request);
        return toResponse(perfilContratanteRepository.save(perfil));
    }

    @Transactional
    public PerfilContratanteResponse atualizar(Long id, PerfilContratanteRequest request) {
        exigirProprioUsuario(id);
        PerfilContratante perfil = perfilContratanteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de contratante nao encontrado."));
        preencherPerfil(perfil, request);
        PerfilContratante salvo = perfilContratanteRepository.save(perfil);
        atualizarPerfilCompleto(salvo.getUsuario());
        return toResponse(salvo);
    }

    @Transactional
    public void deletar(Long id) {
        exigirProprioUsuario(id);
        PerfilContratante perfil = perfilContratanteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de contratante nao encontrado."));
        perfilContratanteRepository.delete(perfil);
    }

    private void atualizarPerfilCompleto(Usuario usuario) {
        PerfilContratante perfil = perfilContratanteRepository.findById(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de contratante nao encontrado."));
        boolean completo = perfil.getBiografia() != null && !perfil.getBiografia().isBlank()
                && perfil.getLocalizacao() != null && !perfil.getLocalizacao().isBlank();
        usuario.setPerfilCompleto(completo);
        usuarioRepository.save(usuario);
    }

    private void exigirProprioUsuario(Long usuarioId) {
        Long atualId = authenticatedUserResolver.usuarioAtual()
                .map(Usuario::getId)
                .orElseThrow(() -> new ForbiddenException("Autenticação obrigatória."));
        if (!atualId.equals(usuarioId)) {
            throw new ForbiddenException("Você só pode alterar o próprio perfil.");
        }
    }

    private void preencherPerfil(PerfilContratante perfil, PerfilContratanteRequest request) {
        perfil.setNomeEmpresa(request.getNomeEmpresa());
        perfil.setTipoPerfil(request.getTipoPerfil());
        perfil.setBiografia(request.getBiografia());
        perfil.setLocalizacao(request.getLocalizacao());
        perfil.setBannerUrl(request.getBannerUrl());
    }

    private PerfilContratanteResponse toResponse(PerfilContratante perfil) {
        // RF34: prioridade foto_perfil do perfil > foto do Google (usuarios.foto_perfil) > DiceBear
        String avatarUrl = avatarService.resolverUrl(
                perfil.getUsuarioId(),
                perfil.getUsuario().getFotoPerfil(),  // foto salva via Google (RF32)
                perfil.getFotoPerfil()                // foto definida pelo usuario via RF08
        );

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
