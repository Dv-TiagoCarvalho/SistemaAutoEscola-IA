package br.com.cfc.shared.domain;

import br.com.cfc.shared.exception.NaoEncontradoException;
import br.com.cfc.shared.security.SecurityUtils;

import java.util.Optional;

/**
 * Segunda trava do multi-tenant. O @TenantId já filtra as consultas, mas aqui conferimos de novo,
 * depois de carregar por id, que o registro é mesmo da autoescola do usuário logado.
 * Registro de outra autoescola responde "não encontrado", igual a um id que não existe.
 */
public final class Tenants {

    private Tenants() {
    }

    public static <T extends TenantEntity> T daAutoescolaAtual(Optional<T> encontrado, String mensagemNaoEncontrado) {
        Long atual = SecurityUtils.autoescolaIdObrigatorio();
        return encontrado
                .filter(e -> atual.equals(e.getAutoescolaId()))
                .orElseThrow(() -> new NaoEncontradoException(mensagemNaoEncontrado));
    }
}
