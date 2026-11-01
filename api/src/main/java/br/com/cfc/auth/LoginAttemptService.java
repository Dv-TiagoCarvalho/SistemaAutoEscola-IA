package br.com.cfc.auth;

import br.com.cfc.shared.config.AppProperties;
import br.com.cfc.shared.exception.MuitasTentativasException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit do login, em memória:
 * <ul>
 *   <li>por e-mail: após N senhas erradas, o e-mail fica bloqueado por X minutos;</li>
 *   <li>por IP: no máximo M tentativas por minuto, para frear varredura de vários e-mails.</li>
 * </ul>
 * Funciona para uma instância da API. Com várias réplicas, troque o armazenamento por Redis.
 */
@Service
public class LoginAttemptService {

    private static final Duration JANELA_IP = Duration.ofMinutes(1);

    private final Map<String, Falhas> falhasPorEmail = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> tentativasPorIp = new ConcurrentHashMap<>();
    private final int maxFalhas;
    private final Duration bloqueio;
    private final int maxTentativasIp;
    private final Clock clock;

    public LoginAttemptService(AppProperties props, Clock clock) {
        this.maxFalhas = props.security().login().maxFalhas();
        this.bloqueio = Duration.ofMinutes(props.security().login().bloqueioMinutos());
        this.maxTentativasIp = props.security().login().maxTentativasPorMinutoIp();
        this.clock = clock;
    }

    /** Chamar ANTES de conferir a senha. Lança 429 se o e-mail estiver bloqueado ou o IP exceder o limite. */
    public void verificarPermitido(String email, String ip) {
        Instant agora = clock.instant();
        if (ip != null) {
            Deque<Instant> tentativas = tentativasPorIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
            synchronized (tentativas) {
                while (!tentativas.isEmpty() && !tentativas.peekFirst().isAfter(agora.minus(JANELA_IP))) {
                    tentativas.pollFirst();
                }
                if (tentativas.size() >= maxTentativasIp) {
                    throw new MuitasTentativasException("Muitas tentativas de login. Aguarde um minuto e tente novamente.");
                }
                tentativas.addLast(agora);
            }
        }
        Falhas falhas = falhasPorEmail.get(email);
        if (falhas != null && falhas.bloqueadoAte != null && agora.isBefore(falhas.bloqueadoAte)) {
            long minutos = Math.max(1, Duration.between(agora, falhas.bloqueadoAte).toMinutes());
            throw new MuitasTentativasException(
                    "Login bloqueado por excesso de senhas incorretas. Tente novamente em " + minutos + " minuto(s).");
        }
    }

    public void registrarFalha(String email) {
        Instant agora = clock.instant();
        falhasPorEmail.compute(email, (k, atual) -> {
            Falhas f = (atual == null || (atual.bloqueadoAte != null && !agora.isBefore(atual.bloqueadoAte)))
                    ? new Falhas() : atual;
            f.quantidade++;
            f.ultimaEm = agora;
            if (f.quantidade >= maxFalhas) {
                f.bloqueadoAte = agora.plus(bloqueio);
            }
            return f;
        });
    }

    public void registrarSucesso(String email) {
        falhasPorEmail.remove(email);
    }

    /** Remove registros antigos para a memória não crescer indefinidamente. */
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void limpar() {
        Instant agora = clock.instant();
        falhasPorEmail.entrySet().removeIf(e -> {
            Falhas f = e.getValue();
            Instant referencia = f.bloqueadoAte != null ? f.bloqueadoAte : f.ultimaEm.plus(bloqueio);
            return agora.isAfter(referencia);
        });
        tentativasPorIp.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Instant ultima = e.getValue().peekLast();
                return ultima == null || agora.isAfter(ultima.plus(JANELA_IP));
            }
        });
    }

    private static final class Falhas {
        int quantidade;
        Instant ultimaEm;
        Instant bloqueadoAte;
    }
}
