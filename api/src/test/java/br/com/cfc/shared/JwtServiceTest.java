package br.com.cfc.shared;

import br.com.cfc.TestSupport;
import br.com.cfc.shared.security.JwtService;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.shared.security.UsuarioAutenticado;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final TestSupport.RelogioFalso relogio = new TestSupport.RelogioFalso();
    private final JwtService jwt = new JwtService(TestSupport.props(), relogio);

    @Test
    void geraEValidaToken() {
        var u = new UsuarioAutenticado(7L, 3L, Perfil.INSTRUTOR, "Igor", "igor@x.com");
        assertThat(jwt.validar(jwt.gerarAccessToken(u))).contains(u);
    }

    @Test
    void superAdminSemAutoescola() {
        var u = new UsuarioAutenticado(1L, null, Perfil.SUPER_ADMIN, "Root", "root@x.com");
        assertThat(jwt.validar(jwt.gerarAccessToken(u)).orElseThrow().autoescolaId()).isNull();
    }

    @Test
    void rejeitaExpiradoEAdulterado() {
        String token = jwt.gerarAccessToken(new UsuarioAutenticado(7L, 3L, Perfil.ADMIN, "A", "a@x.com"));
        assertThat(jwt.validar(token + "x")).isEmpty();
        relogio.avancar(Duration.ofMinutes(16));
        assertThat(jwt.validar(token)).isEmpty();
    }
}
