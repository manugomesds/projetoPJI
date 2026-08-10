package com.portifolio.security;

import com.portifolio.model.Usuario;
import com.portifolio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario nao encontrado: " + email));

        // RF32: usuarios Google nao possuem senha local (campo e null no banco).
        // O placeholder {noop}GOOGLE_USER satisfaz a validacao interna do Spring Security
        // sem comprometer a seguranca — a autenticacao ja foi feita pelo JWT.
        String senha = usuario.getSenha() != null ? usuario.getSenha() : "{noop}GOOGLE_USER";

        return User.builder()
                .username(usuario.getEmail())
                .password(senha)
                .roles(usuario.getTipoUsuario().name())
                .build();
    }
}