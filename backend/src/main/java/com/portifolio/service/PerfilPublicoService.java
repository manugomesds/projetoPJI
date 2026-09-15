package com.portifolio.service;

import com.portifolio.dto.ArtistaPublicoResponse;
import com.portifolio.dto.ContratantePublicoResponse;
import com.portifolio.dto.PerfilPublicoResponse;
import com.portifolio.dto.FuncaoResponse;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PerfilPublicoService {

    private static final int IDADE_ADULTA = 18;

    private final PerfilArtistaRepository perfilArtistaRepository;
    private final PerfilContratanteRepository perfilContratanteRepository;
    private final AvatarService avatarService;
    private final com.portifolio.repository.ItemSalvoRepository itemSalvoRepository;

    @Transactional(readOnly = true)
    public PerfilPublicoResponse buscar(TipoUsuario tipo, Long usuarioId) {
        return switch (tipo) {
            case ARTISTA -> buscarArtista(usuarioId);
            case CONTRATANTE -> buscarContratante(usuarioId);
            default -> throw perfilNaoEncontrado();
        };
    }

    private ArtistaPublicoResponse buscarArtista(Long usuarioId) {
        PerfilArtista perfil = perfilArtistaRepository.buscarPublicoPorUsuarioId(usuarioId)
                .orElseThrow(this::perfilNaoEncontrado);
        Usuario usuario = exigirPublicavel(perfil.getUsuario(), TipoUsuario.ARTISTA);

        Set<FuncaoResponse> funcoes = perfil.getFuncoes().stream()
                .sorted(Comparator.comparing(funcao -> funcao.getNome(), String.CASE_INSENSITIVE_ORDER))
                .map(funcao -> FuncaoResponse.builder().id(funcao.getId()).areaId(funcao.getArea().getId()).nome(funcao.getNome()).build())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return ArtistaPublicoResponse.builder()
                .usuarioId(perfil.getUsuarioId())
                .nomeExibicao(usuario.getNome())
                .biografia(perfil.getBiografia())
                .localizacao(perfil.getLocalizacao())
                .urlPortfolio(perfil.getUrlPortfolio())
                .bannerUrl(perfil.getBannerUrl())
                .avatarUrl(avatarService.resolverUrl(
                        perfil.getUsuarioId(), usuario.getFotoPerfil(), null))
                .funcoes(funcoes)
                .quantidadeSalvos(itemSalvoRepository.countByTipoAlvoAndAlvoId(
                        com.portifolio.model.enums.TipoAlvoSalvo.PERFIL_ARTISTA,usuarioId))
                .build();
    }

    private ContratantePublicoResponse buscarContratante(Long usuarioId) {
        PerfilContratante perfil = perfilContratanteRepository.buscarPublicoPorUsuarioId(usuarioId)
                .orElseThrow(this::perfilNaoEncontrado);
        Usuario usuario = exigirPublicavel(perfil.getUsuario(), TipoUsuario.CONTRATANTE);
        String nomeEmpresa = perfil.getNomeEmpresa();
        String nomeExibicao = nomeEmpresa == null || nomeEmpresa.isBlank()
                ? usuario.getNome()
                : nomeEmpresa;

        return ContratantePublicoResponse.builder()
                .usuarioId(perfil.getUsuarioId())
                .nomeExibicao(nomeExibicao)
                .nomeEmpresa(nomeEmpresa)
                .tipoPerfil(perfil.getTipoPerfil())
                .biografia(perfil.getBiografia())
                .localizacao(perfil.getLocalizacao())
                .bannerUrl(perfil.getBannerUrl())
                .avatarUrl(avatarService.resolverUrl(
                        perfil.getUsuarioId(), usuario.getFotoPerfil(), null))
                .build();
    }

    private Usuario exigirPublicavel(Usuario usuario, TipoUsuario tipoEsperado) {
        if (usuario == null || usuario.getTipoUsuario() != tipoEsperado || ehMenor(usuario)) {
            throw perfilNaoEncontrado();
        }
        return usuario;
    }

    private boolean ehMenor(Usuario usuario) {
        LocalDate nascimento = usuario.getDataNascimento();
        return nascimento == null || nascimento.plusYears(IDADE_ADULTA).isAfter(LocalDate.now());
    }

    private ResourceNotFoundException perfilNaoEncontrado() {
        return new ResourceNotFoundException("Perfil publico nao encontrado.");
    }
}
