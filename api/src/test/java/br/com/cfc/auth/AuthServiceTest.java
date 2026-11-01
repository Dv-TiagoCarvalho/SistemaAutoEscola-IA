package br.com.cfc.auth;

import br.com.cfc.TestSupport;
import br.com.cfc.autoescola.Autoescola;
import br.com.cfc.autoescola.AutoescolaRepository;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.exception.AcessoNegadoException;
import br.com.cfc.shared.exception.NaoAutorizadoException;
import br.com.cfc.shared.security.JwtService;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.usuario.Usuario;
import br.com.cfc.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final AutoescolaRepository autoescolas = mock(AutoescolaRepository.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final TestSupport.RelogioFalso relogio = new TestSupport.RelogioFalso();
    private AuthService service;
    private Usuario usuario;
    private Autoescola autoescola;

    @BeforeEach
    void setUp() {
        var props = TestSupport.props();
        service = new AuthService(usuarios, autoescolas, tokens, encoder, new JwtService(props, relogio),
                new LoginAttemptService(props, relogio), mock(AuditService.class), props, relogio);
        usuario = new Usuario();
        usuario.setId(10L);
        usuario.setAutoescolaId(1L);
        usuario.setNome("Ana");
        usuario.setEmail("ana@x.com");
        usuario.setPerfil(Perfil.ADMIN);
        usuario.setSenhaHash(encoder.encode("Senha123"));
        autoescola = new Autoescola();
        autoescola.setId(1L);
        autoescola.setNome("CFC");
        autoescola.setAtivo(true);
        when(usuarios.findByEmail("ana@x.com")).thenReturn(Optional.of(usuario));
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario));
        when(autoescolas.findById(1L)).thenReturn(Optional.of(autoescola));
        when(tokens.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void loginOkNormalizaEmail() {
        var r = service.login("  ANA@x.com ", "Senha123", "1.1.1.1");
        assertThat(r.accessToken()).isNotBlank();
        assertThat(r.refreshToken()).isNotBlank();
        assertThat(r.usuario().autoescolaNome()).isEqualTo("CFC");
    }

    @Test
    void senhaErradaOuEmailInexistenteMesmaMensagem() {
        assertThatThrownBy(() -> service.login("ana@x.com", "errada1", null))
                .isInstanceOf(NaoAutorizadoException.class).hasMessage("E-mail ou senha incorretos.");
        assertThatThrownBy(() -> service.login("nao@x.com", "errada1", null))
                .isInstanceOf(NaoAutorizadoException.class).hasMessage("E-mail ou senha incorretos.");
    }

    @Test
    void bloqueiaUsuarioInativoEAutoescolaSuspensa() {
        usuario.setAtivo(false);
        assertThatThrownBy(() -> service.login("ana@x.com", "Senha123", null)).isInstanceOf(AcessoNegadoException.class);
        usuario.setAtivo(true);
        autoescola.setAtivo(false);
        assertThatThrownBy(() -> service.login("ana@x.com", "Senha123", null)).isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void refreshRotacionaToken() {
        var antigo = new RefreshToken(10L, "h", relogio.instant(), relogio.instant().plus(Duration.ofDays(30)));
        when(tokens.findByTokenHash(AuthService.hash("abc"))).thenReturn(Optional.of(antigo));
        var r = service.refresh("abc");
        assertThat(antigo.isRevogado()).isTrue();
        assertThat(r.refreshToken()).isNotEqualTo("abc");
    }

    @Test
    void reusoDeTokenRevogadoEncerraTodasAsSessoes() {
        var revogado = new RefreshToken(10L, "h", relogio.instant(), relogio.instant().plus(Duration.ofDays(30)));
        revogado.setRevogadoEm(relogio.instant());
        when(tokens.findByTokenHash(AuthService.hash("abc"))).thenReturn(Optional.of(revogado));
        assertThatThrownBy(() -> service.refresh("abc")).isInstanceOf(NaoAutorizadoException.class);
        verify(tokens).revogarTodosDoUsuario(eq(10L), any());
    }

    @Test
    void refreshExpiradoFalha() {
        var t = new RefreshToken(10L, "h", relogio.instant(), relogio.instant().plus(Duration.ofDays(1)));
        when(tokens.findByTokenHash(AuthService.hash("abc"))).thenReturn(Optional.of(t));
        relogio.avancar(Duration.ofDays(2));
        assertThatThrownBy(() -> service.refresh("abc")).isInstanceOf(NaoAutorizadoException.class);
    }
}
