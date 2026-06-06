package com.portifolio.service;

import com.portifolio.dto.CadastroRequest;
import com.portifolio.dto.CadastroResponse;
import com.portifolio.dto.LoginRequest;
import com.portifolio.dto.LoginResponse;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.Usuario;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.security.JwtService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public CadastroResponse cadastrar(CadastroRequest request) {

        // Verificar e-mail duplicado
        if (usuarioRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ConflictException("Este email ja esta cadastrado.");
        }

        // Calcular idade
        int idade = Period.between(request.getDataNascimento(), LocalDate.now()).getYears();
        boolean menorDeIdade = idade < 18;

        // Validar responsavel legal se menor de 18
        if (menorDeIdade) {
            if (request.getNomeResponsavel() == null || request.getNomeResponsavel().isBlank()) {
                throw new IllegalArgumentException("Nome do responsavel e obrigatorio para menores de 18 anos.");
            }
            if (request.getTelefoneResponsavel() == null || request.getTelefoneResponsavel().isBlank()) {
                throw new IllegalArgumentException("Telefone do responsavel e obrigatorio para menores de 18 anos.");
            }
            if (request.getEmailResponsavel() == null || request.getEmailResponsavel().isBlank()) {
                throw new IllegalArgumentException("Email do responsavel e obrigatorio para menores de 18 anos.");
            }
        }

        // Criar usuario
        Usuario usuario = new Usuario();
        usuario.setNome(request.getNome());
        usuario.setDataNascimento(request.getDataNascimento());
        usuario.setTelefone(request.getTelefone());
        usuario.setEmail(request.getEmail());
        usuario.setSenha(passwordEncoder.encode(request.getSenha()));
        usuario.setTipoUsuario(request.getTipoUsuario());
        usuario.setPerfilCompleto(false);
        usuario.setDataCriacao(LocalDateTime.now());

        if (menorDeIdade) {
            usuario.setNomeResponsavel(request.getNomeResponsavel());
            usuario.setTelefoneResponsavel(request.getTelefoneResponsavel());
            usuario.setEmailResponsavel(request.getEmailResponsavel());
        }

        Usuario salvo = usuarioRepository.save(usuario);

        return CadastroResponse.builder()
                .id(salvo.getId())
                .nome(salvo.getNome())
                .email(salvo.getEmail())
                .tipoUsuario(salvo.getTipoUsuario())
                .menorDeIdade(menorDeIdade)
                .mensagem("Cadastro realizado com sucesso! Faca login.")
                .build();
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Email ou senha incorretos."));

        if (!passwordEncoder.matches(request.getSenha(), usuario.getSenha())) {
            throw new ResourceNotFoundException("Email ou senha incorretos.");
        }

        String token = jwtService.gerarToken(usuario);

        return LoginResponse.builder()
                .token(token)
                .id(usuario.getId())
                .nome(usuario.getNome())
                .email(usuario.getEmail())
                .tipoUsuario(usuario.getTipoUsuario())
                .perfilCompleto(usuario.getPerfilCompleto())
                .build();
    }
}