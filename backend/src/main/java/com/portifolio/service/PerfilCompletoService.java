package com.portifolio.service;

import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fonte única da regra server-side de perfil completo do RF08. */
@Service
@RequiredArgsConstructor
public class PerfilCompletoService {

    private final UsuarioRepository usuarioRepository;
    private final PerfilArtistaRepository perfilArtistaRepository;
    private final PerfilContratanteRepository perfilContratanteRepository;

    @Transactional
    public boolean recalcular(Usuario usuario) {
        boolean completo = switch (usuario.getTipoUsuario()) {
            case ARTISTA -> perfilArtistaRepository.findById(usuario.getId())
                    .map(perfil -> calcularArtista(usuario, perfil))
                    .orElse(false);
            case CONTRATANTE -> perfilContratanteRepository.findById(usuario.getId())
                    .map(perfil -> calcularContratante(usuario, perfil))
                    .orElse(false);
        };
        usuario.setPerfilCompleto(completo);
        usuarioRepository.save(usuario);
        return completo;
    }

    public boolean calcularArtista(Usuario usuario, PerfilArtista perfil) {
        return cadastroCompleto(usuario)
                && preenchido(perfil.getBiografia())
                && preenchido(perfil.getLocalizacao());
    }

    public boolean calcularContratante(Usuario usuario, PerfilContratante perfil) {
        return cadastroCompleto(usuario)
                && preenchido(perfil.getBiografia())
                && preenchido(perfil.getLocalizacao());
    }

    private boolean cadastroCompleto(Usuario usuario) {
        return preenchido(usuario.getNome())
                && dataNascimentoValida(usuario.getDataNascimento())
                && preenchido(usuario.getTelefone())
                && preenchido(usuario.getEmail())
                && responsavelValidoQuandoMenor(usuario);
    }

    private boolean dataNascimentoValida(LocalDate dataNascimento) {
        return dataNascimento != null && !dataNascimento.isAfter(LocalDate.now());
    }

    private boolean responsavelValidoQuandoMenor(Usuario usuario) {
        if (Period.between(usuario.getDataNascimento(), LocalDate.now()).getYears() >= 18) {
            return true;
        }
        return preenchido(usuario.getNomeResponsavel())
                && preenchido(usuario.getTelefoneResponsavel())
                && preenchido(usuario.getEmailResponsavel());
    }

    private boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
