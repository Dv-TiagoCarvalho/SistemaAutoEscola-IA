package br.com.cfc.matricula;

import br.com.cfc.autoescola.Categoria;
import br.com.cfc.financeiro.OrigemTitulo;
import br.com.cfc.financeiro.StatusTitulo;
import br.com.cfc.financeiro.TituloReceber;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class MatriculaDtos {

    private MatriculaDtos() {
    }

    public record MatriculaRequest(
            @NotNull(message = "Informe o processo do aluno.") Long processoId,
            @NotNull(message = "Informe o pacote.") Long pacoteId,
            @DecimalMin(value = "0.00", message = "O desconto não pode ser negativo.") @Digits(integer = 8, fraction = 2, message = "Desconto inválido.") BigDecimal desconto,
            @DecimalMin(value = "0.00", message = "A entrada não pode ser negativa.") @Digits(integer = 8, fraction = 2, message = "Entrada inválida.") BigDecimal entrada,
            @NotNull(message = "Informe o número de parcelas.") @Min(value = 1, message = "Mínimo de 1 parcela.") @Max(value = 48, message = "Máximo de 48 parcelas.") Integer numParcelas,
            LocalDate primeiroVencimento) {
    }

    public record AulaAvulsaRequest(
            @NotNull(message = "Informe a quantidade.") @Min(value = 1, message = "Mínimo de 1 aula.") @Max(value = 50, message = "Máximo de 50 aulas por vez.") Integer quantidade,
            @NotNull(message = "Informe o valor de cada aula.") @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.") @Digits(integer = 6, fraction = 2, message = "Valor inválido.") BigDecimal valorUnitario,
            @NotNull(message = "Informe o vencimento.") LocalDate vencimento) {
    }

    public record CancelamentoRequest(
            @NotBlank(message = "Informe o motivo do cancelamento.") @Size(max = 300, message = "Motivo com no máximo 300 caracteres.") String motivo) {
    }

    public record MatriculaResponse(Long id, Long processoId, Long alunoId, String alunoNome, Categoria categoria,
                                    String pacoteNome, int aulasPraticas, boolean teoricoIncluso,
                                    LocalDate dataMatricula, BigDecimal valorBruto, BigDecimal desconto,
                                    BigDecimal valorTotal, StatusMatricula status, String motivoCancelamento,
                                    boolean temContrato) {
        public static MatriculaResponse de(Matricula m, String alunoNome) {
            return new MatriculaResponse(m.getId(), m.getProcessoId(), m.getAlunoId(), alunoNome, m.getCategoria(),
                    m.getPacoteNome(), m.getAulasPraticas(), m.isTeoricoIncluso(), m.getDataMatricula(),
                    m.getValorBruto(), m.getDesconto(), m.getValorTotal(), m.getStatus(), m.getMotivoCancelamento(),
                    m.getContratoPath() != null);
        }
    }

    public record CreditoResponse(Long id, OrigemCredito origem, int quantidade, BigDecimal valorUnitario,
                                  String descricao) {
        public static CreditoResponse de(CreditoAula c) {
            return new CreditoResponse(c.getId(), c.getOrigem(), c.getQuantidade(), c.getValorUnitario(), c.getDescricao());
        }
    }

    public record TituloResponse(Long id, OrigemTitulo origem, String descricao, int numeroParcela, int totalParcelas,
                                 BigDecimal valor, LocalDate vencimento, StatusTitulo status, boolean vencido) {
        public static TituloResponse de(TituloReceber t, LocalDate hoje) {
            return new TituloResponse(t.getId(), t.getOrigem(), t.getDescricao(), t.getNumeroParcela(),
                    t.getTotalParcelas(), t.getValor(), t.getVencimento(), t.getStatus(),
                    t.getStatus() == StatusTitulo.ABERTO && t.getVencimento().isBefore(hoje));
        }
    }

    public record MatriculaDetalheResponse(MatriculaResponse matricula, List<CreditoResponse> creditos,
                                           List<TituloResponse> titulos, int aulasContratadas) {
    }

    /** Snapshot para auditoria. */
    public record MatriculaAuditoria(Long id, Long alunoId, Long processoId, String pacoteNome, BigDecimal valorTotal,
                                     StatusMatricula status) {
        public static MatriculaAuditoria de(Matricula m) {
            return new MatriculaAuditoria(m.getId(), m.getAlunoId(), m.getProcessoId(), m.getPacoteNome(),
                    m.getValorTotal(), m.getStatus());
        }
    }
}
