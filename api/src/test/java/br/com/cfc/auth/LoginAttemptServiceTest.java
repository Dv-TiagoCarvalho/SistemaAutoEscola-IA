package br.com.cfc.auth;

import br.com.cfc.TestSupport;
import br.com.cfc.shared.exception.MuitasTentativasException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private final TestSupport.RelogioFalso relogio = new TestSupport.RelogioFalso();
    private final LoginAttemptService service = new LoginAttemptService(TestSupport.props(), relogio); // 3 falhas, 15 min, 5/min por IP

    @Test
    void bloqueiaEmailAposFalhasEDesbloqueiaDepois() {
        for (int i = 0; i < 3; i++) {
            service.registrarFalha("a@x.com");
        }
        assertThatThrownBy(() -> service.verificarPermitido("a@x.com", null)).isInstanceOf(MuitasTentativasException.class);
        assertThatCode(() -> service.verificarPermitido("b@x.com", null)).doesNotThrowAnyException();
        relogio.avancar(Duration.ofMinutes(16));
        assertThatCode(() -> service.verificarPermitido("a@x.com", null)).doesNotThrowAnyException();
    }

    @Test
    void sucessoZeraFalhas() {
        service.registrarFalha("a@x.com");
        service.registrarFalha("a@x.com");
        service.registrarSucesso("a@x.com");
        service.registrarFalha("a@x.com");
        assertThatCode(() -> service.verificarPermitido("a@x.com", null)).doesNotThrowAnyException();
    }

    @Test
    void limitaTentativasPorIp() {
        for (int i = 0; i < 5; i++) {
            service.verificarPermitido("u" + i + "@x.com", "10.0.0.1");
        }
        assertThatThrownBy(() -> service.verificarPermitido("outro@x.com", "10.0.0.1")).isInstanceOf(MuitasTentativasException.class);
        relogio.avancar(Duration.ofSeconds(61));
        assertThatCode(() -> service.verificarPermitido("outro@x.com", "10.0.0.1")).doesNotThrowAnyException();
    }
}
