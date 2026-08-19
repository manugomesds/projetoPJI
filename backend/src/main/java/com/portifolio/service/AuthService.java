package com.portifolio.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.portifolio.dto.CadastroRequest;
import com.portifolio.dto.CadastroResponse;
import com.portifolio.dto.GoogleAuthRequest;
import com.portifolio.dto.GoogleAuthResponse;
import com.portifolio.dto.LoginRequest;
import com.portifolio.dto.LoginResponse;
import com.portifolio.dto.RefreshRequest;
import com.portifolio.dto.RefreshResponse;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.Usuario;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.security.JwtService;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Collections;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PerfilArtistaRepository perfilArtistaRepository;
    private final PerfilContratanteRepository perfilContratanteRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AvatarService avatarService;

    @Value("${google.client-id}")
    private String googleClientId;

    // Instancia criada uma vez no startup (evita hit na JWK endpoint do Google a cada chamada)
    private GoogleIdTokenVerifier googleVerifier;

    @PostConstruct
    public void init() {
        googleVerifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    // ──────────────────────────────────────────────────────────
    // RF01 — Cadastro convencional (sem alteracao de logica)
    // ──────────────────────────────────────────────────────────

    @Transactional
    public CadastroResponse cadastrar(CadastroRequest request) {

        if (usuarioRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ConflictException("Este email ja esta cadastrado.");
        }

        LocalDate hoje = LocalDate.now();
        if (request.getDataNascimento().isAfter(hoje)) {
            throw new IllegalArgumentException("Data de nascimento não pode estar no futuro.");
        }
        int idade = Period.between(request.getDataNascimento(), hoje).getYears();
        boolean menorDeIdade = idade < 18;

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
        criarPerfilInicial(salvo, request.getTipoPerfilContratante());

        return CadastroResponse.builder()
                .id(salvo.getId())
                .nome(salvo.getNome())
                .email(salvo.getEmail())
                .tipoUsuario(salvo.getTipoUsuario())
                .menorDeIdade(menorDeIdade)
                .mensagem("Cadastro realizado com sucesso! Faca login.")
                .build();
    }

    private void criarPerfilInicial(Usuario usuario, String tipoPerfilContratante) {
        if (usuario.getTipoUsuario() == TipoUsuario.CONTRATANTE) {
            PerfilContratante perfil = new PerfilContratante();
            perfil.setUsuario(usuario);
            perfil.setTipoPerfil(tipoPerfilContratante);
            perfilContratanteRepository.save(perfil);
            return;
        }

        PerfilArtista perfil = new PerfilArtista();
        perfil.setUsuario(usuario);
        perfilArtistaRepository.save(perfil);
    }

    // ──────────────────────────────────────────────────────────
    // RF02 — Login convencional
    // RF33 — "Lembrar de mim" (rememberMe)
    // RF34 — avatarUrl no response
    // ──────────────────────────────────────────────────────────

    // @Transactional (nao readOnly) pois RF33 pode escrever refresh_token no banco
    @Transactional
    public LoginResponse login(LoginRequest request) {

        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Email ou senha incorretos."));

        // RF32: guard — usuario Google nao tem senha local
        if (usuario.getSenha() == null) {
            throw new IllegalArgumentException("Esta conta usa login via Google. Use o botao 'Entrar com Google'.");
        }

        if (!passwordEncoder.matches(request.getSenha(), usuario.getSenha())) {
            throw new ResourceNotFoundException("Email ou senha incorretos.");
        }

        String token = jwtService.gerarToken(usuario);

        // RF33: gera refresh token apenas se rememberMe = true
        String refreshToken = null;
        if (Boolean.TRUE.equals(request.getRememberMe())) {
            refreshToken = refreshTokenService.gerarRefreshToken(usuario);
        }

        // RF34: resolve avatar a partir da foto do usuario (perfil ainda pode nao existir)
        String avatarUrl = avatarService.resolverUrl(usuario.getId(), usuario.getFotoPerfil(), null);

        return LoginResponse.builder()
                .token(token)
                .id(usuario.getId())
                .nome(usuario.getNome())
                .email(usuario.getEmail())
                .tipoUsuario(usuario.getTipoUsuario())
                .perfilCompleto(usuario.getPerfilCompleto())
                .avatarUrl(avatarUrl)
                .refreshToken(refreshToken)
                .build();
    }

    // ──────────────────────────────────────────────────────────
    // RF32 — Login com Google (OAuth2)
    // ──────────────────────────────────────────────────────────

    @Transactional
    public GoogleAuthResponse loginComGoogle(GoogleAuthRequest request) {

        // 1. Valida o ID Token com a chave publica do Google
        GoogleIdToken.Payload payload = validarTokenGoogle(request.getIdToken());

        String googleId = payload.getSubject();
        String email    = payload.getEmail();
        String nome     = (String) payload.get("name");
        String foto     = (String) payload.get("picture");

        // 2. Busca usuario por googleId (retorno) ou por email (vinculacao de conta existente)
        Optional<Usuario> usuarioOpt = usuarioRepository.findByGoogleId(googleId);
        if (usuarioOpt.isEmpty()) {
            usuarioOpt = usuarioRepository.findByEmail(email);
        }

        // 3. Usuario ja existe — faz login direto
        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();

            // Vincula o googleId se o usuario cadastrou convencionalmente antes
            if (usuario.getGoogleId() == null) {
                usuario.setGoogleId(googleId);
                // RF34 Opcao B: salva foto do Google apenas se nao tiver foto propria
                if (usuario.getFotoPerfil() == null) {
                    usuario.setFotoPerfil(foto);
                }
                usuarioRepository.save(usuario);
            }

            return autenticarUsuario(usuario, request.getRememberMe());
        }

        // 4. Usuario novo — verifica se frontend enviou os dados obrigatorios
        boolean dadosInsuficientes = request.getTipoUsuario() == null
                || request.getDataNascimento() == null
                || request.getTelefone() == null
                || request.getTelefone().isBlank();

        if (dadosInsuficientes) {
            // Retorna dados do Google para o frontend pre-preencher o form de conclusao
            return GoogleAuthResponse.builder()
                    .status("AGUARDANDO_DADOS")
                    .nomeGoogle(nome)
                    .emailGoogle(email)
                    .fotoGoogle(foto)
                    .build();
        }

        // 5. Cria o usuario com os dados do Google + dados complementares do request
        // RF32: menores de 18 precisam usar cadastro convencional (campos de responsavel nao estao no fluxo Google)
        LocalDate hoje = LocalDate.now();
        if (request.getDataNascimento().isAfter(hoje)) {
            throw new IllegalArgumentException("Data de nascimento não pode estar no futuro.");
        }
        int idade = Period.between(request.getDataNascimento(), hoje).getYears();
        if (idade < 18) {
            throw new IllegalArgumentException(
                    "Cadastro de menores de 18 anos requer responsavel legal. Use o cadastro convencional.");
        }

        Usuario novoUsuario = new Usuario();
        novoUsuario.setGoogleId(googleId);
        novoUsuario.setNome(nome);
        novoUsuario.setEmail(email);
        novoUsuario.setFotoPerfil(foto);     // RF34 Opcao B: foto Google como ponto de partida
        novoUsuario.setSenha(null);           // sem senha local
        novoUsuario.setTipoUsuario(request.getTipoUsuario());
        novoUsuario.setDataNascimento(request.getDataNascimento());
        novoUsuario.setTelefone(request.getTelefone());
        novoUsuario.setPerfilCompleto(false);
        novoUsuario.setDataCriacao(LocalDateTime.now());

        Usuario salvo = usuarioRepository.save(novoUsuario);
        criarPerfilInicial(salvo, null);
        return autenticarUsuario(salvo, request.getRememberMe());
    }

    // ──────────────────────────────────────────────────────────
    // RF33 — Renovar Access Token via Refresh Token
    // ──────────────────────────────────────────────────────────

    @Transactional
    public RefreshResponse refreshToken(RefreshRequest request) {
        Usuario usuario = refreshTokenService.validarRefreshToken(request.getRefreshToken());
        String novoToken = jwtService.gerarToken(usuario);
        return RefreshResponse.builder().token(novoToken).build();
    }

    // ──────────────────────────────────────────────────────────
    // RF33 — Logout (invalida o refresh token informado)
    // ──────────────────────────────────────────────────────────

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenService.invalidarRefreshToken(request.getRefreshToken());
    }

    // ──────────────────────────────────────────────────────────
    // Helpers privados
    // ──────────────────────────────────────────────────────────

    private GoogleAuthResponse autenticarUsuario(Usuario usuario, Boolean rememberMe) {
        String token = jwtService.gerarToken(usuario);

        String refreshToken = null;
        if (Boolean.TRUE.equals(rememberMe)) {
            refreshToken = refreshTokenService.gerarRefreshToken(usuario);
        }

        String avatarUrl = avatarService.resolverUrl(usuario.getId(), usuario.getFotoPerfil(), null);

        return GoogleAuthResponse.builder()
                .status("AUTENTICADO")
                .token(token)
                .refreshToken(refreshToken)
                .id(usuario.getId())
                .nome(usuario.getNome())
                .email(usuario.getEmail())
                .tipoUsuario(usuario.getTipoUsuario())
                .perfilCompleto(usuario.getPerfilCompleto())
                .avatarUrl(avatarUrl)
                .build();
    }

    private GoogleIdToken.Payload validarTokenGoogle(String rawToken) {
        try {
            GoogleIdToken idToken = googleVerifier.verify(rawToken);
            if (idToken == null) {
                throw new IllegalArgumentException("Token do Google invalido ou expirado.");
            }
            return idToken.getPayload();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("Falha ao validar token do Google.");
        }
    }
}
