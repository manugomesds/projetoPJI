package com.portifolio.service;

import com.portifolio.dto.CadastroRequest;
import com.portifolio.dto.CadastroResponse;
import com.portifolio.dto.GoogleAuthRequest;
import com.portifolio.dto.GoogleAuthResponse;
import com.portifolio.dto.LoginRequest;
import com.portifolio.dto.LoginResponse;
import com.portifolio.dto.RefreshRequest;
import com.portifolio.dto.RefreshResponse;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ForbiddenException;
import com.portifolio.exception.UnauthorizedException;
import com.portifolio.model.Usuario;
import com.portifolio.model.AreaArtistica;
import com.portifolio.model.PerfilArtistaArea;
import com.portifolio.repository.AreaArtisticaRepository;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.PerfilContratante;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.model.enums.StatusConta;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.PerfilContratanteRepository;
import com.portifolio.repository.UsuarioRepository;
import com.portifolio.security.JwtService;
import com.portifolio.security.GoogleAccountAccessPolicy;
import com.portifolio.service.google.GoogleLinkLock;
import com.portifolio.service.google.GoogleTokenClaims;
import com.portifolio.service.google.GoogleTokenVerifier;
import com.portifolio.validation.PasswordPolicy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PerfilArtistaRepository perfilArtistaRepository;
    private final PerfilContratanteRepository perfilContratanteRepository;
    private final AreaArtisticaRepository areaArtisticaRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AvatarService avatarService;
    private final PasswordPolicy passwordPolicy;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final GoogleLinkLock googleLinkLock;

    // ──────────────────────────────────────────────────────────
    // RF01 — Cadastro convencional e vínculo com uma área principal
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
        if (idade < 14) {
            throw new IllegalArgumentException("A idade mínima para cadastro é 14 anos.");
        }
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

        validarTipoCadastro(request.getTipoUsuario());
        AreaArtistica areaPrincipal = validarPerfilInicial(request.getTipoUsuario(),
                request.getTipoPerfilArtistico(), request.getAreaPrincipalId());
        Usuario usuario = new Usuario();
        usuario.setNome(request.getNome());
        usuario.setDataNascimento(request.getDataNascimento());
        usuario.setTelefone(request.getTelefone());
        usuario.setEmail(request.getEmail());
        usuario.setSenha(passwordPolicy.encode(request.getSenha()));
        usuario.setTipoUsuario(request.getTipoUsuario());
        usuario.setPerfilCompleto(false);
        usuario.setStatusConta(StatusConta.PENDENTE_VERIFICACAO_EMAIL);
        usuario.setDataCriacao(LocalDateTime.now());

        if (menorDeIdade) {
            usuario.setNomeResponsavel(request.getNomeResponsavel());
            usuario.setTelefoneResponsavel(request.getTelefoneResponsavel());
            usuario.setEmailResponsavel(request.getEmailResponsavel());
        }

        Usuario salvo = usuarioRepository.save(usuario);
        criarPerfilInicial(salvo, request.getTipoPerfilContratante(), request.getTipoPerfilArtistico(), areaPrincipal);

        return CadastroResponse.builder()
                .id(salvo.getId())
                .nome(salvo.getNome())
                .email(salvo.getEmail())
                .tipoUsuario(salvo.getTipoUsuario())
                .menorDeIdade(menorDeIdade)
                .mensagem("Cadastro realizado com sucesso! Faca login.")
                .build();
    }

    private void criarPerfilInicial(Usuario usuario, String tipoPerfilContratante, com.portifolio.model.enums.TipoPerfilArtistico tipoArtistico, AreaArtistica areaPrincipal) {
        if (usuario.getTipoUsuario() == TipoUsuario.CONTRATANTE) {
            PerfilContratante perfil = new PerfilContratante();
            perfil.setUsuario(usuario);
            perfil.setTipoPerfil(tipoPerfilContratante);
            perfilContratanteRepository.save(perfil);
            return;
        }

        PerfilArtista perfil = new PerfilArtista();
        perfil.setUsuario(usuario);
        perfil.setTipoPerfilArtistico(tipoArtistico);
        PerfilArtistaArea vinculo = new PerfilArtistaArea();
        vinculo.setPerfil(perfil);
        vinculo.setArea(areaPrincipal);
        vinculo.setPrincipal(true);
        perfil.getAreas().add(vinculo);
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
                .orElseThrow(() -> new UnauthorizedException("Email ou senha incorretos."));

        // RF32: guard — usuario Google nao tem senha local
        if (usuario.getSenha() == null) {
            throw new IllegalArgumentException("Esta conta usa login via Google. Use o botao 'Entrar com Google'.");
        }

        if (!passwordEncoder.matches(request.getSenha(), usuario.getSenha())) {
            throw new UnauthorizedException("Email ou senha incorretos.");
        }

        exigirAcessoNormalGoogle(usuario);
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
        GoogleTokenClaims claims = googleTokenVerifier.verificar(request.getIdToken());

        String googleId = claims.subject();
        String email = claims.email();
        String nome = claims.nome();
        String foto = claims.foto();
        validarDadosGoogleNoSchema(googleId, email, nome, foto);
        googleLinkLock.bloquear(email, googleId);

        // 2. Resolve os dois identificadores antes de vincular para detectar conflitos.
        Optional<Usuario> usuarioPorGoogle = usuarioRepository.findByGoogleId(googleId);
        Optional<Usuario> usuarioPorEmail = usuarioRepository.findByEmailIgnoreCase(email);

        // 3. Reutiliza a conta; o provedor não altera seu estado ou completude.
        if (usuarioPorGoogle.isPresent()) {
            Usuario usuario = usuarioPorGoogle.get();
            if (usuarioPorEmail.isPresent()
                    && !Objects.equals(usuario.getId(), usuarioPorEmail.get().getId())) {
                throw falhaGoogle();
            }
            return autenticarUsuario(usuario, request.getRememberMe());
        }
        if (usuarioPorEmail.isPresent()) {
            Usuario usuario = usuarioPorEmail.get();
            if (usuario.getGoogleId() != null
                    && !usuario.getGoogleId().equals(googleId)) {
                throw falhaGoogle();
            }
            if (usuario.getGoogleId() == null) {
                usuario.setGoogleId(googleId);
                if (usuario.getFotoPerfil() == null) {
                    usuario.setFotoPerfil(foto);
                }
                try {
                    usuarioRepository.saveAndFlush(usuario);
                } catch (DataIntegrityViolationException ex) {
                    throw falhaGoogle();
                }
            }
            return autenticarUsuario(usuario, request.getRememberMe());
        }

        // 4. O schema exige esses três campos até para uma conta provisória.
        // Sem eles, mantém AGUARDANDO_DADOS sem inventar valores nem emitir JWT.
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

        // 5. Persistência provisória somente com os campos obrigatórios do schema.
        // A conclusão do RF01 e o consentimento não são inferidos de um login Google.
        LocalDate hoje = LocalDate.now();
        if (request.getDataNascimento().isAfter(hoje)) {
            throw new IllegalArgumentException("Data de nascimento não pode estar no futuro.");
        }
        int idade = Period.between(request.getDataNascimento(), hoje).getYears();
        if (idade < 14 || (request.getTipoUsuario() == TipoUsuario.CONTRATANTE && idade < 18)) {
            throw new IllegalArgumentException("Idade insuficiente para o tipo de cadastro informado.");
        }

        validarTipoCadastro(request.getTipoUsuario());
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
        novoUsuario.setEmailVerificado(true);
        novoUsuario.setStatusConta(StatusConta.PENDENTE_TIPO_PERFIL);
        novoUsuario.setDataCriacao(LocalDateTime.now());

        try {
            Usuario salvo = usuarioRepository.saveAndFlush(novoUsuario);
            return autenticarUsuario(salvo, request.getRememberMe());
        } catch (DataIntegrityViolationException ex) {
            throw falhaGoogle();
        }
    }

    // ──────────────────────────────────────────────────────────
    // RF33 — Renovar Access Token via Refresh Token
    // ──────────────────────────────────────────────────────────

    @Transactional
    public RefreshResponse refreshToken(RefreshRequest request) {
        Usuario usuario = refreshTokenService.validarRefreshToken(request.getRefreshToken());
        exigirAcessoNormalGoogle(usuario);
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
        if (!GoogleAccountAccessPolicy.acessoNormalPermitido(usuario)) {
            if (usuario.getStatusConta() == StatusConta.BLOQUEADA) {
                throw new ForbiddenException("Conta indisponível para autenticação.");
            }
            return GoogleAuthResponse.builder()
                    .status("AGUARDANDO_DADOS")
                    .statusConta(usuario.getStatusConta())
                    .id(usuario.getId())
                    .nomeGoogle(usuario.getNome())
                    .emailGoogle(usuario.getEmail())
                    .fotoGoogle(usuario.getFotoPerfil())
                    .tipoUsuario(usuario.getTipoUsuario())
                    .perfilCompleto(usuario.getPerfilCompleto())
                    .build();
        }
        String token = jwtService.gerarToken(usuario);

        String refreshToken = null;
        if (Boolean.TRUE.equals(rememberMe)) {
            refreshToken = refreshTokenService.gerarRefreshToken(usuario);
        }

        String avatarUrl = avatarService.resolverUrl(usuario.getId(), usuario.getFotoPerfil(), null);

        return GoogleAuthResponse.builder()
                .status("AUTENTICADO")
                .statusConta(usuario.getStatusConta())
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

    private void exigirAcessoNormalGoogle(Usuario usuario) {
        if (!GoogleAccountAccessPolicy.acessoNormalPermitido(usuario)) {
            throw new ForbiddenException("Conta Google sem acesso normal: conclusão cadastral ou consentimento pendente, ou conta bloqueada.");
        }
    }

    private void validarDadosGoogleNoSchema(String googleId, String email, String nome, String foto) {
        if (googleId == null || googleId.isBlank() || googleId.length() > 255) {
            throw falhaGoogle();
        }
        if (email == null || email.isBlank() || email.length() > 150) {
            throw falhaGoogle();
        }
        if (nome == null || nome.isBlank() || nome.length() > 150) {
            throw falhaGoogle();
        }
        if (foto != null && foto.length() > 255) {
            throw falhaGoogle();
        }
    }

    private UnauthorizedException falhaGoogle() {
        return new UnauthorizedException(GoogleTokenVerifier.MENSAGEM_ERRO);
    }
    private void validarTipoCadastro(TipoUsuario tipo) {
        if (tipo != TipoUsuario.ARTISTA && tipo != TipoUsuario.CONTRATANTE) {
            throw new IllegalArgumentException("Cadastro permitido somente para ARTISTA ou CONTRATANTE.");
        }
    }

    private AreaArtistica validarPerfilInicial(TipoUsuario tipo,
            com.portifolio.model.enums.TipoPerfilArtistico perfil, Short areaPrincipalId) {
        if (tipo != TipoUsuario.ARTISTA) return null;
        if (perfil == null || areaPrincipalId == null) {
            throw new IllegalArgumentException("Informe tipoPerfilArtistico e areaPrincipalId para o artista.");
        }
        return areaArtisticaRepository.findById(areaPrincipalId)
                .orElseThrow(() -> new IllegalArgumentException("Área artística principal não encontrada."));
    }
}
