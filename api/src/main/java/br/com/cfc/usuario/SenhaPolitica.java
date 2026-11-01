package br.com.cfc.usuario;

/** Regra de senha: 8 a 72 caracteres (limite do BCrypt), com pelo menos uma letra e um número. */
public final class SenhaPolitica {

    public static final String REGEX = "^(?=.*[A-Za-zÀ-ÿ])(?=.*\\d).{8,72}$";
    public static final String MENSAGEM = "A senha deve ter de 8 a 72 caracteres, com pelo menos uma letra e um número.";

    private SenhaPolitica() {
    }
}
