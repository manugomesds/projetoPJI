package com.portifolio.service;

import com.portifolio.dto.PerfilContratanteRequest;
import com.portifolio.dto.PerfilContratanteResponse;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.exception.ForbiddenException;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Usuario;
import com.portifolio.repository.PerfilContratanteRepository;
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
    private final AvatarService avatarService; // RF34
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final PerfilCompletoService perfilCompletoService;

    @Transactional(readOnly = true)
    public List<PerfilContratanteResponse> listarTodos() {
        return perfilContratanteRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PerfilContratanteResponse buscarPorId(Long id) {
        exigirContratanteAtual(id);
        PerfilContratante perfil = perfilContratanteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de contratante nao encontrado."));
        return toResponse(perfil);
    }

    @Transactional
    public PerfilContratanteResponse criar(PerfilContratanteRequest request) {
        Usuario usuarioAtual = exigirContratanteAtual(request.getUsuarioId());
        if (perfilContratanteRepository.existsById(request.getUsuarioId())) {
            throw new ConflictException("Perfil de contratante ja cadastrado para este usuario.");
        }
        PerfilContratante perfil = new PerfilContratante();
        perfil.setUsuario(usuarioAtual);
        preencherPerfil(perfil, request);
        PerfilContratante salvo = perfilContratanteRepository.save(perfil);
        perfilCompletoService.recalcular(usuarioAtual);
        return toResponse(salvo);
    }

    @Transactional
    public PerfilContratanteResponse atualizar(Long id, PerfilContratanteRequest request) {
        Usuario usuarioAtual = exigirContratanteAtual(id);
        validarIdDoPayload(id, request.getUsuarioId());
        PerfilContratante perfil = perfilContratanteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de contratante nao encontrado."));
        preencherPerfil(perfil, request);
        PerfilContratante salvo = perfilContratanteRepository.save(perfil);
        perfilCompletoService.recalcular(usuarioAtual);
        return toResponse(salvo);
    }

    @Transactional
    public void deletar(Long id) {
        Usuario usuarioAtual = exigirContratanteAtual(id);
        PerfilContratante perfil = perfilContratanteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de contratante nao encontrado."));
        perfilContratanteRepository.delete(perfil);
        perfilContratanteRepository.flush();
        perfilCompletoService.recalcular(usuarioAtual);
    }

    private Usuario exigirContratanteAtual(Long usuarioId) {
        Usuario atual = authenticatedUserResolver.usuarioAtual()
                .orElseThrow(() -> new ForbiddenException("Autenticação obrigatória."));
        if (!atual.getId().equals(usuarioId)) {
            throw new ForbiddenException("Você só pode alterar o próprio perfil.");
        }
        if (atual.getTipoUsuario() != com.portifolio.model.enums.TipoUsuario.CONTRATANTE) {
            throw new ForbiddenException("Somente contratantes podem alterar perfil de contratante.");
        }
        return atual;
    }

    private void validarIdDoPayload(Long id, Long usuarioId) {
        if (!id.equals(usuarioId)) {
            throw new ForbiddenException("O usuário do payload deve corresponder ao perfil autenticado.");
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
