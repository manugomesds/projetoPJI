package com.portifolio.service;

import com.portifolio.dto.UsuarioRequest;
import com.portifolio.dto.UsuarioResponse;
import com.portifolio.dto.UsuarioAtualizacaoRequest;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.Usuario;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.security.AuthenticatedUserResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
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

    @Transactional(readOnly = true)
    public UsuarioResponse buscarAtual() {
        return toResponse(usuarioAtual());
    }

    @Transactional
    public UsuarioResponse atualizarAtual(UsuarioAtualizacaoRequest request) {
        Usuario usuario = usuarioAtual();
        usuarioRepository.findByEmail(request.getEmail())
                .filter(existente -> !existente.getId().equals(usuario.getId()))
                .ifPresent(existente -> {
                    throw new ConflictException("E-mail já cadastrado.");
                });

        usuario.setNome(request.getNome());
        usuario.setDataNascimento(request.getDataNascimento());
        usuario.setTelefone(request.getTelefone());
        usuario.setEmail(request.getEmail());
        if (request.getNovaSenha() != null && !request.getNovaSenha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(request.getNovaSenha()));
        }
        return toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public void deletarAtual() {
        usuarioRepository.delete(usuarioAtual());
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarTodos() {
        return usuarioRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        return toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse criar(UsuarioRequest request) {
        if (usuarioRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ConflictException("E-mail já cadastrado.");
        }
        if (request.getSenha() == null || request.getSenha().isBlank()) {
            throw new IllegalArgumentException("Senha é obrigatória.");
        }
        Usuario usuario = new Usuario();
        preencherUsuario(usuario, request);
        usuario.setSenha(passwordEncoder.encode(request.getSenha()));
        if (usuario.getPerfilCompleto() == null) {
            usuario.setPerfilCompleto(false);
        }
        usuario.setDataCriacao(LocalDateTime.now());
        return toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        usuarioRepository.findByEmail(request.getEmail())
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new ConflictException("E-mail já cadastrado.");
                });
        preencherUsuario(usuario, request);
        if (request.getSenha() != null && !request.getSenha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(request.getSenha()));
        }
        return toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public void deletar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        usuarioRepository.delete(usuario);
    }

    private void preencherUsuario(Usuario usuario, UsuarioRequest request) {
        usuario.setNome(request.getNome());
        usuario.setDataNascimento(request.getDataNascimento());
        usuario.setTelefone(request.getTelefone());
        usuario.setEmail(request.getEmail());
        usuario.setTipoUsuario(request.getTipoUsuario());
        usuario.setPerfilCompleto(request.getPerfilCompleto());
        usuario.setTokenRecuperacao(request.getTokenRecuperacao());
        usuario.setTokenExpiracao(request.getTokenExpiracao());
        usuario.setNomeResponsavel(request.getNomeResponsavel());
        usuario.setTelefoneResponsavel(request.getTelefoneResponsavel());
        usuario.setEmailResponsavel(request.getEmailResponsavel());
    }

    private UsuarioResponse toResponse(Usuario usuario) {
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

    private Usuario usuarioAtual() {
        return authenticatedUserResolver.usuarioAtual()
                .orElseThrow(() -> new ResourceNotFoundException("Usuário autenticado não encontrado."));
    }
}
