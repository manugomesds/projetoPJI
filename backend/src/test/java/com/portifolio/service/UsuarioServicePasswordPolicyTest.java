package com.portifolio.service;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.portifolio.dto.UsuarioRequest;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.security.AuthenticatedUserResolver;
import com.portifolio.validation.PasswordPolicy;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServicePasswordPolicyTest {

    @Mock UsuarioRepository usuarioRepository;
    @Mock BCryptPasswordEncoder passwordEncoder;
    @Mock AuthenticatedUserResolver authenticatedUserResolver;
    @Mock PerfilCompletoService perfilCompletoService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock PasswordPolicy passwordPolicy;
    @InjectMocks UsuarioService usuarioService;

    @Test
    void metodoPublicoDeCriacaoNaoPersisteNemCodificaForaDaPolicyCentral() {
        UsuarioRequest request = new UsuarioRequest();
        request.setNome("Tentativa alternativa");
        request.setDataNascimento(LocalDate.of(1990, 1, 1));
        request.setTelefone("11999999999");
        request.setEmail("alternativo@rnf19.test");
        request.setSenha("artista123");
        request.setTipoUsuario(TipoUsuario.ARTISTA);
        when(usuarioRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(passwordPolicy.encode(request.getSenha()))
                .thenThrow(new IllegalArgumentException(PasswordPolicy.MESSAGE));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> usuarioService.criar(request))
                .withMessage(PasswordPolicy.MESSAGE);

        verify(passwordPolicy).encode(request.getSenha());
        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }
}
