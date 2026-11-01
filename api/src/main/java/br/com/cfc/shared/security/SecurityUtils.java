package br.com.cfc.shared.security;

import br.com.cfc.shared.exception.AcessoNegadoException;
import br.com.cfc.shared.exception.NaoAutorizadoException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<UsuarioAutenticado> usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return Optional.of(usuario);
        }
        return Optional.empty();
    }

    public static UsuarioAutenticado usuarioObrigatorio() {
        return usuarioAtual().orElseThrow(() -> new NaoAutorizadoException("Usuário não autenticado."));
    }

    /** Autoescola do usuário logado. Lança 403 para quem não pertence a uma autoescola (SUPER_ADMIN). */
    public static Long autoescolaIdObrigatorio() {
        UsuarioAutenticado usuario = usuarioObrigatorio();
        if (usuario.autoescolaId() == null) {
            throw new AcessoNegadoException("Esta operação exige um usuário vinculado a uma autoescola.");
        }
        return usuario.autoescolaId();
    }
}
