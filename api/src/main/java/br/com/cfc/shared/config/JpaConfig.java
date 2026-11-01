package br.com.cfc.shared.config;

import br.com.cfc.shared.security.SecurityUtils;
import br.com.cfc.shared.security.UsuarioAutenticado;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaConfig {

    @Bean
    AuditorAware<Long> auditorAware() {
        return () -> SecurityUtils.usuarioAtual().map(UsuarioAutenticado::id);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
