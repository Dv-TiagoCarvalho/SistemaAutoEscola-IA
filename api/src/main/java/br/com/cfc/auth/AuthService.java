package br.com.cfc.auth;

import br.com.cfc.auth.AuthDtos.TokenResponse;
import br.com.cfc.auth.AuthDtos.UsuarioLogadoResponse;
import br.com.cfc.autoescola.Autoescola;
import br.com.cfc.autoescola.AutoescolaRepository;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.config.AppProperties;
import br.com.cfc.shared.exception.AcessoNegadoException;
import br.com.cfc.shared.exception.NaoAutorizadoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.JwtService;
import br.com.cfc.shared.security.SecurityUtils;
import br.com.cfc.usuario.Usuario;
import br.com.cfc.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String MSG_CREDENCIAIS = "E-mail ou senha incorretos.";
    private static final String MSG_SESSAO = "Sessão expirada. Faça login novamente.";

    private final UsuarioRepository usuarioRepository;
    private final AutoescolaRepository autoescolaRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    private final Clock clock;
    private final Duration validadeRefresh;
    private final SecureRandom random = new SecureRandom();
    /** Hash fixo usado quando o e-mail não existe, para o tempo de resposta não revelar se o e-mail está cadastrado. */
    private final String hashFalso;

    public AuthService(UsuarioRepository usuarioRepository, AutoescolaRepository autoescolaRepository,
                       RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, LoginAttemptService loginAttemptService, AuditService auditService,
                       AppProperties props, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.autoescolaRepository = autoescolaRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
        this.clock = clock;
        this.validadeRefresh = Duration.ofDays(props.security().refreshTokenDays());
        this.hashFalso = passwordEncoder.encode("senha-inexistente-" + random.nextLong());
    }

    @Transactional
    public TokenResponse login(String email, String senha, String ip) {
        String emailNormalizado = Usuario.normalizarEmail(email);
        loginAttemptService.verificarPermitido(emailNormalizado, ip);

        Optional<Usuario> encontrado = usuarioRepository.findByEmail(emailNormalizado);
        boolean senhaConfere = passwordEncoder.matches(senha, encontrado.map(Usuario::getSenhaHash).orElse(hashFalso));
        if (encontrado.isEmpty() || !senhaConfere) {
            loginAttemptService.registrarFalha(emailNormalizado);
            throw new NaoAutorizadoException(MSG_CREDENCIAIS);
        }

        Usuario usuario = encontrado.get();
        Autoescola autoescola = validarAcesso(usuario);
        loginAttemptService.registrarSucesso(emailNormalizado);
        usuario.setUltimoLogin(clock.instant());
        auditService.registrar(usuario.getAutoescolaId(), "usuario", usuario.getId(), AcaoAuditoria.LOGIN, null, null);
        return emitirTokens(usuario, autoescola);
    }

    @Transactional(noRollbackFor = NaoAutorizadoException.class)
    public TokenResponse refresh(String refreshToken) {
        RefreshToken atual = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new NaoAutorizadoException(MSG_SESSAO));
        Instant agora = clock.instant();

        if (atual.isRevogado()) {
            // Token já usado: alguém pode ter copiado. Encerra todas as sessões do usuário.
            log.warn("Reuso de refresh token revogado detectado para o usuário {}", atual.getUsuarioId());
            refreshTokenRepository.revogarTodosDoUsuario(atual.getUsuarioId(), agora);
            throw new NaoAutorizadoException(MSG_SESSAO);
        }
        if (atual.isExpirado(agora)) {
            throw new NaoAutorizadoException(MSG_SESSAO);
        }

        Usuario usuario = usuarioRepository.findById(atual.getUsuarioId())
                .orElseThrow(() -> new NaoAutorizadoException(MSG_SESSAO));
        Autoescola autoescola = validarAcesso(usuario);
        atual.setRevogadoEm(agora);
        return emitirTokens(usuario, autoescola);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(t -> !t.isRevogado())
                .ifPresent(t -> t.setRevogadoEm(clock.instant()));
    }

    @Transactional(readOnly = true)
    public UsuarioLogadoResponse usuarioLogado() {
        Long id = SecurityUtils.usuarioObrigatorio().id();
        Usuario usuario = usuarioRepository.findById(id).orElseThrow(() -> new NaoAutorizadoException(MSG_SESSAO));
        Autoescola autoescola = usuario.getAutoescolaId() == null ? null
                : autoescolaRepository.findById(usuario.getAutoescolaId()).orElse(null);
        return paraResponse(usuario, autoescola);
    }

    @Transactional
    public void alterarPropriaSenha(String senhaAtual, String novaSenha) {
        Long id = SecurityUtils.usuarioObrigatorio().id();
        Usuario usuario = usuarioRepository.findById(id).orElseThrow(() -> new NaoAutorizadoException(MSG_SESSAO));
        if (!passwordEncoder.matches(senhaAtual, usuario.getSenhaHash())) {
            throw new RegraNegocioException("A senha atual está incorreta.");
        }
        if (passwordEncoder.matches(novaSenha, usuario.getSenhaHash())) {
            throw new RegraNegocioException("A nova senha deve ser diferente da atual.");
        }
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        // Encerra as sessões em outros aparelhos; o app atual faz login de novo com a senha nova.
        refreshTokenRepository.revogarTodosDoUsuario(usuario.getId(), clock.instant());
        auditService.registrar(usuario.getAutoescolaId(), "usuario", usuario.getId(), AcaoAuditoria.SENHA, null, null);
    }

    /** Apaga refresh tokens vencidos há mais de 7 dias (uma vez por dia). */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void limparTokensVencidos() {
        int apagados = refreshTokenRepository.apagarExpiradosAntesDe(clock.instant().minus(Duration.ofDays(7)));
        if (apagados > 0) {
            log.info("{} refresh tokens vencidos apagados", apagados);
        }
    }

    private Autoescola validarAcesso(Usuario usuario) {
        if (!usuario.isAtivo()) {
            throw new AcessoNegadoException("Usuário desativado. Procure o administrador da autoescola.");
        }
        if (usuario.getAutoescolaId() == null) {
            return null; // SUPER_ADMIN
        }
        Autoescola autoescola = autoescolaRepository.findById(usuario.getAutoescolaId())
                .orElseThrow(() -> new AcessoNegadoException("Autoescola não encontrada."));
        if (!autoescola.isAtivo()) {
            throw new AcessoNegadoException("O acesso desta autoescola está suspenso. Entre em contato com o suporte.");
        }
        return autoescola;
    }

    private TokenResponse emitirTokens(Usuario usuario, Autoescola autoescola) {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant agora = clock.instant();
        refreshTokenRepository.save(new RefreshToken(usuario.getId(), hash(refresh), agora, agora.plus(validadeRefresh)));

        String access = jwtService.gerarAccessToken(usuario.comoAutenticado());
        return new TokenResponse(access, refresh, "Bearer", jwtService.validadeEmSegundos(), paraResponse(usuario, autoescola));
    }

    private UsuarioLogadoResponse paraResponse(Usuario usuario, Autoescola autoescola) {
        return new UsuarioLogadoResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil(),
                usuario.getAutoescolaId(), autoescola != null ? autoescola.getNome() : null);
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
