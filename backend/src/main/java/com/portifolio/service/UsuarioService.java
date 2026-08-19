package com.portifolio.service;

import com.portifolio.dto.UsuarioAtualizacaoRequest;
import com.portifolio.dto.UsuarioRequest;
import com.portifolio.dto.UsuarioResponse;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ForbiddenException;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.exception.UnprocessableEntityException;
import com.portifolio.model.Usuario;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.security.AuthenticatedUserResolver;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final PerfilCompletoService perfilCompletoService;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public UsuarioResponse buscarAtual() {
        return toResponseCompleto(usuarioAtual());
    }

    @Transactional
    public UsuarioResponse atualizarAtual(UsuarioAtualizacaoRequest request) {
        return atualizarUsuario(usuarioAtual(), request);
    }

    @Transactional
    public void deletarAtual() {
        Usuario usuario = usuarioAtual();
        refreshTokenService.invalidarTodosDoUsuario(usuario.getId());
        usuarioRepository.delete(usuario);
    }

    /** Lista pública compatível, sem dados pessoais ou do responsável legal. */
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarTodos() {
        return usuarioRepository.findAll().stream().map(this::toResponsePublico).toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        return authenticatedUserResolver.usuarioAtual()
                .filter(atual -> atual.getId().equals(id))
                .map(ignorado -> toResponseCompleto(usuario))
                .orElseGet(() -> toResponsePublico(usuario));
    }

    @Transactional
    public UsuarioResponse criar(UsuarioRequest request) {
        validarEmailDisponivel(request.getEmail(), null);
        if (request.getSenha() == null || request.getSenha().isBlank()) {
            throw new IllegalArgumentException("Senha é obrigatória.");
        }
        validarDataNascimento(request.getDataNascimento());

        Usuario usuario = new Usuario();
        usuario.setNome(request.getNome());
        usuario.setDataNascimento(request.getDataNascimento());
        usuario.setTelefone(request.getTelefone());
        usuario.setEmail(request.getEmail());
        usuario.setSenha(passwordEncoder.encode(request.getSenha()));
        usuario.setTipoUsuario(request.getTipoUsuario());
        usuario.setPerfilCompleto(false);
        usuario.setDataCriacao(LocalDateTime.now());
        atualizarResponsavelSeMenor(
                usuario,
                request.getNomeResponsavel(),
                request.getTelefoneResponsavel(),
                request.getEmailResponsavel());
        return toResponseCompleto(usuarioRepository.save(usuario));
    }

    /** Rota legada por ID, limitada à própria conta e ao DTO seguro do RF08. */
    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioAtualizacaoRequest request) {
        Usuario usuario = usuarioAtual();
        if (!usuario.getId().equals(id)) {
            throw new ForbiddenException("Você só pode alterar a própria conta.");
        }
        return atualizarUsuario(usuario, request);
    }

    @Transactional
    public void deletar(Long id) {
        Usuario usuario = usuarioAtual();
        if (!usuario.getId().equals(id)) {
            throw new ForbiddenException("Você só pode excluir a própria conta.");
        }
        refreshTokenService.invalidarTodosDoUsuario(usuario.getId());
        usuarioRepository.delete(usuario);
    }

    private UsuarioResponse atualizarUsuario(
            Usuario usuario, UsuarioAtualizacaoRequest request) {
        validarEmailDisponivel(request.getEmail(), usuario.getId());
        usuario.setNome(request.getNome());
        usuario.setTelefone(request.getTelefone());
        usuario.setEmail(request.getEmail());
        // dataNascimento é deliberadamente imutável, mesmo se o cliente legado a enviar.
        atualizarResponsavelSeMenor(
                usuario,
                request.getNomeResponsavel(),
                request.getTelefoneResponsavel(),
                request.getEmailResponsavel());

        boolean senhaAlterada = atualizarSenhaSeSolicitada(usuario, request);
        Usuario salvo = usuarioRepository.save(usuario);
        perfilCompletoService.recalcular(salvo);
        if (senhaAlterada) {
            refreshTokenService.invalidarTodosDoUsuario(salvo.getId());
        }
        return toResponseCompleto(salvo);
    }

    private boolean atualizarSenhaSeSolicitada(
            Usuario usuario, UsuarioAtualizacaoRequest request) {
        if (request.getNovaSenha() == null || request.getNovaSenha().isBlank()) {
            return false;
        }
        if (usuario.getSenha() == null || usuario.getSenha().isBlank()) {
            throw new UnprocessableEntityException(
                    "Contas exclusivamente Google não podem criar senha por este fluxo.");
        }
        if (request.getSenhaAtual() == null || request.getSenhaAtual().isBlank()) {
            throw new UnprocessableEntityException(
                    "Informe a senha atual para definir uma nova senha.");
        }
        if (!passwordEncoder.matches(request.getSenhaAtual(), usuario.getSenha())) {
            throw new ForbiddenException("Senha atual incorreta.");
        }
        usuario.setSenha(passwordEncoder.encode(request.getNovaSenha()));
        return true;
    }

    private void validarEmailDisponivel(String email, Long usuarioId) {
        usuarioRepository.findByEmail(email)
                .filter(existente -> usuarioId == null || !existente.getId().equals(usuarioId))
                .ifPresent(existente -> {
                    throw new ConflictException("E-mail já cadastrado.");
                });
    }

    private void validarDataNascimento(LocalDate dataNascimento) {
        if (dataNascimento == null) {
            throw new IllegalArgumentException("Data de nascimento é obrigatória.");
        }
        if (dataNascimento.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Data de nascimento não pode estar no futuro.");
        }
    }

    private void atualizarResponsavelSeMenor(
            Usuario usuario,
            String nomeResponsavel,
            String telefoneResponsavel,
            String emailResponsavel) {
        if (!menorDeIdade(usuario.getDataNascimento())) {
            return;
        }
        String nomeFinal = valorInformadoOuAtual(nomeResponsavel, usuario.getNomeResponsavel());
        String telefoneFinal = valorInformadoOuAtual(
                telefoneResponsavel, usuario.getTelefoneResponsavel());
        String emailFinal = valorInformadoOuAtual(emailResponsavel, usuario.getEmailResponsavel());
        if (!preenchido(nomeFinal) || !preenchido(telefoneFinal) || !preenchido(emailFinal)) {
            throw new UnprocessableEntityException(
                    "Nome, telefone e e-mail do responsável são obrigatórios para menores de 18 anos.");
        }
        usuario.setNomeResponsavel(nomeFinal);
        usuario.setTelefoneResponsavel(telefoneFinal);
        usuario.setEmailResponsavel(emailFinal);
    }

    private boolean menorDeIdade(LocalDate dataNascimento) {
        validarDataNascimento(dataNascimento);
        return Period.between(dataNascimento, LocalDate.now()).getYears() < 18;
    }

    private String valorInformadoOuAtual(String novoValor, String valorAtual) {
        return novoValor != null ? novoValor : valorAtual;
    }

    private boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    private UsuarioResponse toResponseCompleto(Usuario usuario) {
        return UsuarioResponse.builder()
                .id(usuario.getId())
                .nome(usuario.getNome())
                .dataNascimento(usuario.getDataNascimento())
                .telefone(usuario.getTelefone())
                .email(usuario.getEmail())
                .tipoUsuario(usuario.getTipoUsuario())
                .perfilCompleto(usuario.getPerfilCompleto())
                .dataCriacao(usuario.getDataCriacao())
                .nomeResponsavel(usuario.getNomeResponsavel())
                .telefoneResponsavel(usuario.getTelefoneResponsavel())
                .emailResponsavel(usuario.getEmailResponsavel())
                .build();
    }

    private UsuarioResponse toResponsePublico(Usuario usuario) {
        return UsuarioResponse.builder()
                .id(usuario.getId())
                .nome(usuario.getNome())
                .tipoUsuario(usuario.getTipoUsuario())
                .perfilCompleto(usuario.getPerfilCompleto())
                .dataCriacao(usuario.getDataCriacao())
                .build();
    }

    private Usuario usuarioAtual() {
        return authenticatedUserResolver.usuarioAtual()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuário autenticado não encontrado."));
    }
}
