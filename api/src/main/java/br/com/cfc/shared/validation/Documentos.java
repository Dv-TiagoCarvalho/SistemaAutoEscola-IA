package br.com.cfc.shared.validation;

import java.util.Locale;

/**
 * Validação e normalização de CPF e CNPJ.
 * O CNPJ já aceita o formato alfanumérico da Receita Federal (12 caracteres [0-9A-Z] + 2 dígitos verificadores),
 * além do numérico tradicional.
 */
public final class Documentos {

    private static final int[] PESOS_CNPJ_DV1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_CNPJ_DV2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private Documentos() {
    }

    /** Remove pontuação; CPF fica só com dígitos. */
    public static String normalizarCpf(String cpf) {
        return cpf == null ? null : cpf.replaceAll("\\D", "");
    }

    /** Remove pontuação e coloca em maiúsculas; mantém letras (CNPJ alfanumérico). */
    public static String normalizarCnpj(String cnpj) {
        return cnpj == null ? null : cnpj.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean cpfValido(String cpf) {
        String n = normalizarCpf(cpf);
        if (n == null || n.length() != 11 || n.chars().distinct().count() == 1) {
            return false;
        }
        int dv1 = dvCpf(n, 9);
        int dv2 = dvCpf(n, 10);
        return dv1 == n.charAt(9) - '0' && dv2 == n.charAt(10) - '0';
    }

    private static int dvCpf(String n, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (n.charAt(i) - '0') * (tamanho + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    public static boolean cnpjValido(String cnpj) {
        String n = normalizarCnpj(cnpj);
        if (n == null || !n.matches("[0-9A-Z]{12}[0-9]{2}")) {
            return false;
        }
        if (n.chars().distinct().count() == 1) {
            return false;
        }
        int dv1 = dvCnpj(n, PESOS_CNPJ_DV1);
        int dv2 = dvCnpj(n, PESOS_CNPJ_DV2);
        return dv1 == n.charAt(12) - '0' && dv2 == n.charAt(13) - '0';
    }

    private static int dvCnpj(String n, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (n.charAt(i) - '0') * pesos[i]; // letras: valor ASCII - 48 (regra da Receita)
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
