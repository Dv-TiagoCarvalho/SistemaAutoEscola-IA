package br.com.cfc.shared.tenant;

/**
 * Guarda, por thread (requisição), qual autoescola está sendo acessada.
 * Preenchido pelo filtro do JWT e limpo no fim de cada requisição.
 */
public final class TenantContext {

    /** Valor usado quando não há tenant: não corresponde a nenhuma autoescola, então nenhuma linha é retornada. */
    public static final Long NENHUM = 0L;
    /** Valor usado pelo SUPER_ADMIN da plataforma: enxerga todas as autoescolas. */
    public static final Long RAIZ = -1L;

    private static final ThreadLocal<Long> ATUAL = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void definir(Long autoescolaId) {
        ATUAL.set(autoescolaId);
    }

    public static void definirRaiz() {
        ATUAL.set(RAIZ);
    }

    public static Long atual() {
        Long valor = ATUAL.get();
        return valor != null ? valor : NENHUM;
    }

    public static void limpar() {
        ATUAL.remove();
    }
}
