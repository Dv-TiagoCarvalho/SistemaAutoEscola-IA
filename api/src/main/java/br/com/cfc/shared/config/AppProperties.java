package br.com.cfc.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Security security, Bootstrap bootstrap) {

    public record Security(
            String jwtSecret,
            long accessTokenMinutes,
            long refreshTokenDays,
            List<String> corsOrigins,
            Login login) {
    }

    public record Login(int maxFalhas, long bloqueioMinutos, int maxTentativasPorMinutoIp) {
    }

    public record Bootstrap(String superadminEmail, String superadminSenha, boolean seedExemplo) {
    }
}
