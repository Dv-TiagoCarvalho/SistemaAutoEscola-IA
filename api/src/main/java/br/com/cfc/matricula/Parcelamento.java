package br.com.cfc.matricula;

import br.com.cfc.shared.exception.RegraNegocioException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Divide um valor em entrada + parcelas mensais, sem perder centavos.
 * Calcula em centavos (inteiros) para a soma das parcelas ser sempre exatamente o total.
 * Os centavos que sobram da divisão vão para as primeiras parcelas (R$ 1.000,00 em 3x = 333,34 + 333,33 + 333,33).
 */
public final class Parcelamento {

    public static final int MAX_PARCELAS = 48;

    public record Parcela(int numero, String descricao, BigDecimal valor, LocalDate vencimento) {
    }

    private Parcelamento() {
    }

    /**
     * @param entrada            pago na data da matrícula; zero ou nulo se não houver
     * @param numParcelas        quantidade de parcelas do saldo (1 = à vista no 1º vencimento)
     * @param primeiroVencimento vencimento da 1ª parcela; as demais vencem a cada mês, no mesmo dia
     *                           (dia 31 vira o último dia dos meses mais curtos)
     */
    public static List<Parcela> calcular(BigDecimal total, BigDecimal entrada, LocalDate dataEntrada,
                                         int numParcelas, LocalDate primeiroVencimento) {
        if (total == null || total.signum() <= 0) {
            throw new RegraNegocioException("O valor total deve ser maior que zero.");
        }
        BigDecimal valorEntrada = entrada == null ? BigDecimal.ZERO : entrada;
        if (valorEntrada.signum() < 0) {
            throw new RegraNegocioException("A entrada não pode ser negativa.");
        }
        long totalCentavos = centavos(total);
        long entradaCentavos = centavos(valorEntrada);
        if (entradaCentavos >= totalCentavos) {
            throw new RegraNegocioException("A entrada deve ser menor que o valor total. Para pagar à vista, use 1 parcela.");
        }
        if (numParcelas < 1 || numParcelas > MAX_PARCELAS) {
            throw new RegraNegocioException("O número de parcelas deve ficar entre 1 e " + MAX_PARCELAS + ".");
        }
        if (primeiroVencimento == null) {
            throw new RegraNegocioException("Informe o vencimento da primeira parcela.");
        }
        long saldo = totalCentavos - entradaCentavos;
        if (saldo < numParcelas) {
            throw new RegraNegocioException("O valor é pequeno demais para dividir em " + numParcelas + " parcelas.");
        }

        List<Parcela> lista = new ArrayList<>();
        int numero = 1;
        if (entradaCentavos > 0) {
            lista.add(new Parcela(numero++, "Entrada", reais(entradaCentavos), dataEntrada));
        }
        long base = saldo / numParcelas;
        long resto = saldo % numParcelas;
        for (int i = 0; i < numParcelas; i++) {
            long valor = base + (i < resto ? 1 : 0);
            lista.add(new Parcela(numero++, "Parcela " + (i + 1) + "/" + numParcelas, reais(valor),
                    primeiroVencimento.plusMonths(i)));
        }
        return lista;
    }

    private static long centavos(BigDecimal valor) {
        try {
            return valor.movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new RegraNegocioException("Os valores devem ter no máximo 2 casas decimais.");
        }
    }

    private static BigDecimal reais(long centavos) {
        return BigDecimal.valueOf(centavos, 2);
    }
}
