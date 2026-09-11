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
import com.portifolio.validation.PasswordPolicy;
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
    private final PasswordPolicy passwordPolicy;

    @Transactional(readOnly = true)
    public UsuarioResponse buscarAtual() {
        return toResponseCompleto(usuarioAtual());
    }

    @Transactional
    public UsuarioResponse atualizarAtual(UsuarioAtualizacaoRequest request) {
        Usuario usuario = usuarioAtual();
        validarEmailDisponivel(request.getEmail(), usuario.getId());

        usuario.setNome(request.getNome());
        usuario.setTelefone(request.getTelefone());
        usuario.setEmail(request.getEmail());
        // dataNascimento permanece imutável mesmo que o cliente legado a envie.
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

    @Transactional
    public void deletarAtual() {
        usuarioAtual();
        throw exclusaoDeContaIndisponivel();
    }

    /** Lista compatível, sem expor e-mail, telefone, nascimento ou responsável. */
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarTodos() {
        return usuarioRepository.findAll().stream()
                .map(this::toResponsePublico)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        // Dados de terceiros pertencem ao contrato público RF10, inclusive a política de menores.
        return toResponseCompleto(exigirProprioUsuario(id));
    }

    @Transactional
    public UsuarioResponse criar(UsuarioRequest request) {
        if (usuarioRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ConflictException("E-mail já cadastrado.");
        }
        Usuario usuario = new Usuario();
        preencherUsuarioNaCriacao(usuario, request);
        usuario.setSenha(passwordPolicy.encode(request.getSenha()));
        usuario.setPerfilCompleto(false);
        usuario.setDataCriacao(LocalDateTime.now());
        return toResponseCompleto(usuarioRepository.save(usuario));
    }

    /** Rota legada por ID, limitada aos mesmos campos seguros de /me. */
    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioRequest request) {
        Usuario usuario = exigirProprioUsuario(id);
        validarEmailDisponivel(request.getEmail(), id);
        if (request.getSenha() != null && !request.getSenha().isBlank()) {
            throw new UnprocessableEntityException(
                    "Troque a senha por /api/usuarios/me informando a senha atual.");
        }
        usuario.setNome(request.getNome());
        usuario.setTelefone(request.getTelefone());
        usuario.setEmail(request.getEmail());
        atualizarResponsavelSeMenor(
                usuario,
                request.getNomeResponsavel(),
                request.getTelefoneResponsavel(),
                request.getEmailResponsavel());
        Usuario salvo = usuarioRepository.save(usuario);
        perfilCompletoService.recalcular(salvo);
        return toResponseCompleto(salvo);
    }

    @Transactional
    public void deletar(Long id) {
        exigirProprioUsuario(id);
        throw exclusaoDeContaIndisponivel();
    }

    private UnprocessableEntityException exclusaoDeContaIndisponivel() {
        return new UnprocessableEntityException(
                "Exclusão de conta temporariamente indisponível até a implementação segura do RF22.");
    }

    private boolean atualizarSenhaSeSolicitada(Usuario usuario, UsuarioAtualizacaoRequest request) {
        if (request.getNovaSenha() == null || request.getNovaSenha().isEmpty()) {
            return false;
        }
        if (usuario.getSenha() == null || usuario.getSenha().isBlank()) {
            throw new UnprocessableEntityException(
                    "Contas exclusivamente Google não podem criar senha por este fluxo.");
        }
        if (request.getSenhaAtual() == null || request.getSenhaAtual().isBlank()) {
            throw new UnprocessableEntityException("Informe a senha atual para definir uma nova senha.");
        }
        if (!passwordEncoder.matches(request.getSenhaAtual(), usuario.getSenha())) {
            throw new ForbiddenException("Senha atual incorreta.");
        }
        usuario.setSenha(passwordPolicy.encode(request.getNovaSenha()));
        return true;
    }

    private void validarEmailDisponivel(String email, Long usuarioId) {
        usuarioRepository.findByEmail(email)
                .filter(existente -> !existente.getId().equals(usuarioId))
                .ifPresent(existente -> {
                    throw new ConflictException("E-mail já cadastrado.");
                });
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
        String telefoneFinal = valorInformadoOuAtual(telefoneResponsavel, usuario.getTelefoneResponsavel());
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
        return dataNascimento != null
                && Period.between(dataNascimento, LocalDate.now()).getYears() < 18;
    }

    private String valorInformadoOuAtual(String novoValor, String valorAtual) {
        return novoValor != null ? novoValor : valorAtual;
    }

    private boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    private Usuario exigirProprioUsuario(Long id) {
        Usuario atual = usuarioAtual();
        if (!atual.getId().equals(id)) {
            throw new ForbiddenException("Você só pode alterar a própria conta.");
        }
        return atual;
    }

    private void preencherUsuarioNaCriacao(Usuario usuario, UsuarioRequest request) {
        usuario.setNome(request.getNome());
        usuario.setDataNascimento(request.getDataNascimento());
        usuario.setTelefone(request.getTelefone());
        usuario.setEmail(request.getEmail());
        usuario.setTipoUsuario(request.getTipoUsuario());
        usuario.setNomeResponsavel(request.getNomeResponsavel());
        usuario.setTelefoneResponsavel(request.getTelefoneResponsavel());
        usuario.setEmailResponsavel(request.getEmailResponsavel());
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
                .orElseThrow(() -> new ResourceNotFoundException("Usuário autenticado não encontrado."));
    }
}
