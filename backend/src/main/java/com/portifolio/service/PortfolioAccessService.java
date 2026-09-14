package com.portifolio.service;

import com.portifolio.exception.*;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.enums.*;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.security.AuthenticatedUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class PortfolioAccessService {
    private final AuthenticatedUserResolver autenticado;
    private final PerfilArtistaRepository perfis;
    private final PerfilPublicoService publico;

    public PerfilArtista artistaAtual() {
        var usuario = autenticado.usuarioAtual().orElseThrow(() -> new UnauthorizedException("Autenticação necessária."));
        if (usuario.getTipoUsuario() != TipoUsuario.ARTISTA || usuario.getStatusConta() != StatusConta.ATIVA)
            throw new ForbiddenException("Gerenciamento disponível somente para artistas com conta ativa.");
        return perfis.findById(usuario.getId()).orElseThrow(PortfolioAccessService::naoEncontrado);
    }
    public void exigirPublico(Long artista) {
        publico.buscar(TipoUsuario.ARTISTA, artista); // Mesma política RF10/RF27, inclusive acesso direto por ID de arquivo.
        var perfil = perfis.findById(artista).orElseThrow(PortfolioAccessService::naoEncontrado);
        if (perfil.getUsuario().getStatusConta() != StatusConta.ATIVA) throw naoEncontrado();
    }
    public static PageRequest pagina(int page, int size, String... ordem) {
        if (page < 0 || size < 1 || size > 50) throw new UnprocessableEntityException("page deve ser não negativo e size entre 1 e 50.");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ordem));
    }
    public static ResourceNotFoundException naoEncontrado() { return new ResourceNotFoundException("Item de portfólio não encontrado."); }
}
