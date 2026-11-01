package br.com.cfc.financeiro;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FinanceiroDtos {

    private FinanceiroDtos() {
    }

    public record TituloResponse(Long id, Long alunoId, String alunoNome, Long matriculaId, OrigemTitulo origem,
                                 String descricao, int numeroParcela, int totalParcelas, BigDecimal valor,
                                 LocalDate vencimento, StatusTitulo status, boolean vencido,
                                 Long pagamentoId, String reciboNumero, LocalDate dataPagamento) {
    }

    public record TituloAvulsoRequest(
            @NotNull(message = "Informe o aluno.") Long alunoId,
            @NotBlank(message = "Informe a descrição.") @Size(max = 120, message = "Descrição com no máximo 120 caracteres.") String descricao,
            @NotNull(message = "Informe o valor.") @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.") @Digits(integer = 8, fraction = 2, message = "Valor inválido.") BigDecimal valor,
            @NotNull(message = "Informe o vencimento.") LocalDate vencimento) {
    }

    public record BaixaRequest(
            @NotNull(message = "Informe a forma de pagamento.") FormaPagamento forma,
            LocalDate dataPagamento,
            @DecimalMin(value = "0.00", message = "O acréscimo não pode ser negativo.") @Digits(integer = 8, fraction = 2, message = "Acréscimo inválido.") BigDecimal acrescimo,
            @DecimalMin(value = "0.00", message = "O desconto não pode ser negativo.") @Digits(integer = 8, fraction = 2, message = "Desconto inválido.") BigDecimal desconto) {
    }

    public record MotivoRequest(
            @NotBlank(message = "Informe o motivo.") @Size(max = 300, message = "Motivo com no máximo 300 caracteres.") String motivo) {
    }

    public record PagamentoResponse(Long id, Long tituloId, Long alunoId, BigDecimal valor, BigDecimal acrescimo,
                                    BigDecimal desconto, FormaPagamento forma, LocalDate dataPagamento,
                                    String reciboNumero, boolean estornado) {
        public static PagamentoResponse de(Pagamento p) {
            return new PagamentoResponse(p.getId(), p.getTituloId(), p.getAlunoId(), p.getValor(), p.getAcrescimo(),
                    p.getDesconto(), p.getForma(), p.getDataPagamento(), p.getReciboNumero(), p.isEstornado());
        }
    }

    public record DespesaRequest(
            @NotBlank(message = "Informe a descrição.") @Size(max = 120, message = "Descrição com no máximo 120 caracteres.") String descricao,
            @NotNull(message = "Informe a categoria.") CategoriaDespesa categoria,
            @NotNull(message = "Informe o valor.") @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.") @Digits(integer = 8, fraction = 2, message = "Valor inválido.") BigDecimal valor,
            @NotNull(message = "Informe o vencimento.") LocalDate vencimento) {
    }

    public record PagarDespesaRequest(LocalDate dataPagamento) {
    }

    public record DespesaResponse(Long id, String descricao, CategoriaDespesa categoria, BigDecimal valor,
                                  LocalDate vencimento, LocalDate dataPagamento, StatusDespesa status, boolean vencida) {
        public static DespesaResponse de(Despesa d, LocalDate hoje) {
            return new DespesaResponse(d.getId(), d.getDescricao(), d.getCategoria(), d.getValor(), d.getVencimento(),
                    d.getDataPagamento(), d.getStatus(), d.getStatus() == StatusDespesa.ABERTA && d.getVencimento().isBefore(hoje));
        }
    }

    public record MesFluxo(String mes, BigDecimal recebido, BigDecimal despesas) {
    }

    public record ResumoFinanceiro(String mes, BigDecimal aReceber, BigDecimal vencido, long titulosVencidos,
                                   BigDecimal recebidoNoMes, BigDecimal despesasPagasNoMes, BigDecimal despesasEmAberto,
                                   BigDecimal saldoDoMes, List<MesFluxo> ultimosMeses) {
    }
}
