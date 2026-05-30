package com.portifolio.service;

import com.portifolio.dto.PerfilContratanteRequest;
import com.portifolio.dto.PerfilContratanteResponse;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Usuario;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.UsuarioRepository;
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

    @Transactional(readOnly = true)
    public List<PerfilContratanteResponse> listarTodos() {
        return perfilContratanteRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PerfilContratanteResponse buscarPorId(Long id) {
        PerfilContratante perfil = perfilContratanteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de contratante não encontrado."));
        return toResponse(perfil);
    }

    @Transactional
    public PerfilContratanteResponse criar(PerfilContratanteRequest request) {
        if (perfilContratanteRepository.existsById(request.getUsuarioId())) {
            throw new ConflictException("Perfil de contratante já cadastrado para este usuário.");
        }
        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        PerfilContratante perfil = new PerfilContratante();
        perfil.setUsuario(usuario);
        perfil.setUsuarioId(usuario.getId());
        preencherPerfil(perfil, request);
        return toResponse(perfilContratanteRepository.save(perfil));
    }

    @Transactional
    public PerfilContratanteResponse atualizar(Long id, PerfilContratanteRequest request) {
        PerfilContratante perfil = perfilContratanteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de contratante não encontrado."));
        preencherPerfil(perfil, request);
        return toResponse(perfilContratanteRepository.save(perfil));
    }

    @Transactional
    public void deletar(Long id) {
        PerfilContratante perfil = perfilContratanteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de contratante não encontrado."));
        perfilContratanteRepository.delete(perfil);
    }

    private void preencherPerfil(PerfilContratante perfil, PerfilContratanteRequest request) {
        perfil.setNomeEmpresa(request.getNomeEmpresa());
        perfil.setBiografia(request.getBiografia());
        perfil.setLocalizacao(request.getLocalizacao());
        perfil.setBannerUrl(request.getBannerUrl());
    }

    private PerfilContratanteResponse toResponse(PerfilContratante perfil) {
        return PerfilContratanteResponse.builder()
                .usuarioId(perfil.getUsuarioId())
                .nomeEmpresa(perfil.getNomeEmpresa())
                .biografia(perfil.getBiografia())
                .localizacao(perfil.getLocalizacao())
                .bannerUrl(perfil.getBannerUrl())
                .build();
    }
}
