package br.com.cfc;

import br.com.cfc.shared.config.AppProperties;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.shared.security.UsuarioAutenticado;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

public final class TestSupport {

    private TestSupport() {
    }

    public static AppProperties props() {
        return new AppProperties(
                new AppProperties.Security("segredo-de-teste-com-mais-de-32-caracteres!!", 15, 30,
                        List.of("http://localhost"), new AppProperties.Login(3, 15, 5)),
                new AppProperties.Bootstrap(null, null, false));
    }

    public static void logarComo(Long id, Long autoescolaId, Perfil perfil) {
        var u = new UsuarioAutenticado(id, autoescolaId, perfil, "Teste", "teste@x.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, List.of(new SimpleGrantedAuthority(perfil.authority()))));
    }

    /** Relógio controlável nos testes. */
    public static final class RelogioFalso extends Clock {
        private Instant agora = Instant.parse("2026-11-01T12:00:00Z");

        public void avancar(Duration d) {
            agora = agora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
