package com.portifolio.security;

import com.portifolio.model.Usuario;
import com.portifolio.repository.UsuarioRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

// Resolve o usuario autenticado a partir do SecurityContext, quando existir.
// Usado em endpoints publicos que tem comportamento extra para quem esta logado
// (ex.: RF03 - vagas canceladas visiveis so para o artista que se candidatou).
// NUNCA aceita o id do usuario vindo do cliente: sempre deriva do token validado.
@Component
@RequiredArgsConstructor
public class AuthenticatedUserResolver {

    private final UsuarioRepository usuarioRepository;

    public Optional<Usuario> usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return Optional.empty();
        }

        String email = auth.getName();
        return usuarioRepository.findByEmail(email);
    }
}