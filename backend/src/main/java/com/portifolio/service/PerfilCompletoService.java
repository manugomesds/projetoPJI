package com.portifolio.service;

import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regra central e exclusivamente server-side de completude do RF08. */
@Service
@RequiredArgsConstructor
public class PerfilCompletoService {

    private final UsuarioRepository usuarioRepository;
    private final PerfilArtistaRepository perfilArtistaRepository;
    private final PerfilContratanteRepository perfilContratanteRepository;

    @Transactional
    public boolean recalcular(Usuario usuario) {
        boolean completo;
        PerfilArtista perfilArtista = null;

        if (usuario.getTipoUsuario() == TipoUsuario.ARTISTA) {
            Optional<PerfilArtista> perfil = perfilArtistaRepository.findById(usuario.getId());
            perfilArtista = perfil.orElse(null);
            completo = perfil.map(valor -> calcularArtista(usuario, valor)).orElse(false);
        } else if (usuario.getTipoUsuario() == TipoUsuario.CONTRATANTE) {
            completo = perfilContratanteRepository.findById(usuario.getId())
                    .map(perfil -> calcularContratante(usuario, perfil))
                    .orElse(false);
        } else {
            completo = false;
        }

        boolean tornouCompleto = !Boolean.TRUE.equals(usuario.getPerfilCompleto()) && completo;
        usuario.setPerfilCompleto(completo);
        usuarioRepository.save(usuario);

        if (tornouCompleto && perfilArtista != null) {
            perfilArtista.setUltimaAtualizacao(LocalDateTime.now());
            perfilArtistaRepository.save(perfilArtista);
        }
        return completo;
    }

    public boolean calcularArtista(Usuario usuario, PerfilArtista perfil) {
        return cadastroCompleto(usuario)
                && preenchido(perfil.getBiografia())
                && preenchido(perfil.getLocalizacao())
                && preenchido(perfil.getUrlPortfolio())
                && perfil.getTipoPerfilArtistico() != null
                && perfil.getRaioAtuacao() != null
                && perfil.getAreas().stream().filter(com.portifolio.model.PerfilArtistaArea::isPrincipal).count() == 1
                && perfil.getFuncoes() != null
                && !perfil.getFuncoes().isEmpty();
    }

    public boolean calcularContratante(Usuario usuario, PerfilContratante perfil) {
        return cadastroCompleto(usuario)
                && preenchido(perfil.getBiografia())
                && preenchido(perfil.getLocalizacao());
    }

    private boolean cadastroCompleto(Usuario usuario) {
        return preenchido(usuario.getNome())
                && usuario.getDataNascimento() != null
                && preenchido(usuario.getTelefone())
                && preenchido(usuario.getEmail())
                && (preenchido(usuario.getSenha()) || preenchido(usuario.getGoogleId()));
    }

    private boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
