package com.portifolio.service;

import com.portifolio.dto.PerfilArtistaRequest;
import com.portifolio.dto.PerfilArtistaResponse;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ForbiddenException;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.Funcao;
import com.portifolio.model.Usuario;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.FuncaoRepository;
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
    private final FuncaoRepository funcaoRepository;
    private final com.portifolio.repository.AreaArtisticaRepository areaArtisticaRepository;
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
        if (request.getTipoPerfilArtistico() != null) perfil.setTipoPerfilArtistico(request.getTipoPerfilArtistico());
        if (request.getRaioAtuacao() != null) perfil.setRaioAtuacao(request.getRaioAtuacao());
        if (perfil.getTipoPerfilArtistico() == null) {
            throw new IllegalArgumentException("Informe tipoPerfilArtistico.");
        }
        perfil.setBannerUrl(request.getBannerUrl());
        if (request.getFuncaoIds() != null) {
            if (request.getAreaPrincipalId() == null) {
                throw new IllegalArgumentException("Informe areaPrincipalId para editar funções.");
            }
            if (perfil.getAreas().size() > 1) {
                throw new com.portifolio.exception.UnprocessableEntityException("Edição de múltiplas áreas depende do RF08 completo.");
            }
            var area = areaArtisticaRepository.findById(request.getAreaPrincipalId())
                    .orElseThrow(() -> new ResourceNotFoundException("Área artística não encontrada."));
            Set<Funcao> funcoes = resolverFuncoes(request.getFuncaoIds());
            if (funcoes.size() > 5 || funcoes.stream().anyMatch(f -> !f.getArea().getId().equals(area.getId()))) {
                throw new IllegalArgumentException("Informe até cinco funções pertencentes à área.");
            }
            var vinculo = perfil.getAreas().stream().findFirst().orElse(null);
            if (vinculo != null && !vinculo.getArea().getId().equals(area.getId())) {
                throw new com.portifolio.exception.UnprocessableEntityException("Troca de área depende do RF08 completo.");
            }
            if (vinculo == null) {
                vinculo = new com.portifolio.model.PerfilArtistaArea();
                vinculo.setPerfil(perfil);
                vinculo.setArea(area);
                vinculo.setPrincipal(true);
                perfil.getAreas().add(vinculo);
            }
            vinculo.setFuncoes(funcoes);
            vinculo.setUltimaAtualizacao(LocalDateTime.now());
        }
    }

    private Set<Funcao> resolverFuncoes(Set<Long> funcaoIds) {
        List<Funcao> funcoes = funcaoRepository.findAllById(funcaoIds);
        if (funcoes.size() != funcaoIds.size()) {
            throw new ResourceNotFoundException("Uma ou mais funcoes nao foram encontradas.");
        }
        return new HashSet<>(funcoes);
    }

    private PerfilArtistaResponse toResponse(PerfilArtista perfil) {
        Set<Long> funcaoIds = perfil.getFuncoes().stream()
                .map(Funcao::getId)
                .collect(Collectors.toSet());

        // Avatar centralizado em usuarios.foto_perfil_url; fallback DiceBear.
        String avatarUrl = avatarService.resolverUrl(
                perfil.getUsuarioId(),
                perfil.getUsuario().getFotoPerfil(),
                null
        );

        return PerfilArtistaResponse.builder()
                .usuarioId(perfil.getUsuarioId())
                .tipoPerfilArtistico(perfil.getTipoPerfilArtistico())
                .raioAtuacao(perfil.getRaioAtuacao())
                .areaPrincipalId(perfil.getAreas().stream().filter(com.portifolio.model.PerfilArtistaArea::isPrincipal)
                        .map(area -> area.getArea().getId()).findFirst().orElse(null))
                .biografia(perfil.getBiografia())
                .localizacao(perfil.getLocalizacao())
                .urlPortfolio(perfil.getUrlPortfolio())
                .nivelMedalha(null)
                .scoreEngajamento(null)
                .bannerUrl(perfil.getBannerUrl())
                .ultimaAtualizacao(perfil.getUltimaAtualizacao())
                .funcaoIds(funcaoIds)
                .avatarUrl(avatarUrl)
                .build();
    }
}
