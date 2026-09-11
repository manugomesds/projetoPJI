package com.portifolio.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portifolio.exception.ForbiddenException;
import com.portifolio.exception.UnprocessableEntityException;
import com.portifolio.model.Usuario;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.security.AuthenticatedUserResolver;
import com.portifolio.validation.PasswordPolicy;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceDeletionContainmentTest {

    @Mock UsuarioRepository usuarioRepository;
    @Mock BCryptPasswordEncoder passwordEncoder;
    @Mock AuthenticatedUserResolver authenticatedUserResolver;
    @Mock PerfilCompletoService perfilCompletoService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock PasswordPolicy passwordPolicy;
    @InjectMocks UsuarioService usuarioService;

    private Usuario usuarioAtual;

    @BeforeEach
    void prepararUsuarioAtual() {
        usuarioAtual = new Usuario();
        usuarioAtual.setId(10L);
    }

    @Test
    void exclusaoAtualFalhaDeFormaControladaSemApagarUsuario() {
        when(authenticatedUserResolver.usuarioAtual()).thenReturn(Optional.of(usuarioAtual));

        assertThatThrownBy(usuarioService::deletarAtual)
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("temporariamente indisponível");

        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    void exclusaoPropriaPorIdFalhaDeFormaControladaSemApagarUsuario() {
        when(authenticatedUserResolver.usuarioAtual()).thenReturn(Optional.of(usuarioAtual));

        assertThatThrownBy(() -> usuarioService.deletar(10L))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("temporariamente indisponível");

        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    void exclusaoAlheiaPorIdMantemOwnershipSemApagarUsuario() {
        when(authenticatedUserResolver.usuarioAtual()).thenReturn(Optional.of(usuarioAtual));

        assertThatThrownBy(() -> usuarioService.deletar(99L))
                .isInstanceOf(ForbiddenException.class);

        verify(usuarioRepository, never()).delete(any());
    }
}
