package br.com.cfc.shared.security;

import br.com.cfc.shared.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final String ISSUER = "autoescola-api";

    private final SecretKey chave;
    private final Duration validade;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        String segredo = props.security().jwtSecret();
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 caracteres.");
        }
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.validade = Duration.ofMinutes(props.security().accessTokenMinutes());
        this.clock = clock;
    }

    public String gerarAccessToken(UsuarioAutenticado usuario) {
        Instant agora = clock.instant();
        var builder = Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(usuario.id()))
                .claim("perfil", usuario.perfil().name())
                .claim("nome", usuario.nome())
                .claim("email", usuario.email())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(validade)));
        if (usuario.autoescolaId() != null) {
            builder.claim("aut", usuario.autoescolaId());
        }
        return builder.signWith(chave).compact();
    }

    /** Valida assinatura, emissor e expiração. Retorna vazio para qualquer token inválido. */
    public Optional<UsuarioAutenticado> validar(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chave)
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Number aut = claims.get("aut", Number.class);
            return Optional.of(new UsuarioAutenticado(
                    Long.valueOf(claims.getSubject()),
                    aut != null ? aut.longValue() : null,
                    Perfil.valueOf(claims.get("perfil", String.class)),
                    claims.get("nome", String.class),
                    claims.get("email", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long validadeEmSegundos() {
        return validade.toSeconds();
    }
}
