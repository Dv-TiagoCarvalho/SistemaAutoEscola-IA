package br.com.cfc.shared.security;

import br.com.cfc.shared.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lê o "Authorization: Bearer ...", autentica o usuário e define o tenant da requisição.
 * Não é um @Component de propósito: é instanciado no SecurityConfig para não ser registrado duas vezes.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                jwtService.validar(header.substring(7)).ifPresent(this::autenticar);
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.limpar();
            SecurityContextHolder.clearContext();
        }
    }

    private void autenticar(UsuarioAutenticado usuario) {
        var auth = new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority(usuario.perfil().authority())));
        SecurityContextHolder.getContext().setAuthentication(auth);
        if (usuario.isSuperAdmin()) {
            TenantContext.definirRaiz();
        } else {
            TenantContext.definir(usuario.autoescolaId());
        }
    }
}
