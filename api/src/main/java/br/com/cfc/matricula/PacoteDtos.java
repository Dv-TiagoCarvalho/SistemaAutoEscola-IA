package br.com.cfc.matricula;

import br.com.cfc.autoescola.Categoria;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class PacoteDtos {

    private PacoteDtos() {
    }

    public record PacoteRequest(
            @NotNull(message = "Informe a categoria.") Categoria categoria,
            @NotBlank(message = "Informe o nome do pacote.") @Size(max = 100, message = "Nome com no máximo 100 caracteres.") String nome,
            @NotNull(message = "Informe a quantidade de aulas práticas.") @Min(value = 0, message = "Quantidade inválida.") @Max(value = 200, message = "Máximo de 200 aulas.") Integer qtdAulasPraticas,
            @NotNull(message = "Informe se o teórico está incluso.") Boolean teoricoIncluso,
            @NotNull(message = "Informe o preço.") @DecimalMin(value = "0.00", message = "O preço não pode ser negativo.") @Digits(integer = 8, fraction = 2, message = "Preço inválido.") BigDecimal preco) {
    }

    public record PacoteStatusRequest(@NotNull(message = "Informe se o pacote está ativo.") Boolean ativo) {
    }

    public record PacoteResponse(Long id, Categoria categoria, String nome, int qtdAulasPraticas,
                                 boolean teoricoIncluso, BigDecimal preco, boolean ativo) {
        public static PacoteResponse de(Pacote p) {
            return new PacoteResponse(p.getId(), p.getCategoria(), p.getNome(), p.getQtdAulasPraticas(),
                    p.isTeoricoIncluso(), p.getPreco(), p.isAtivo());
        }
    }
}
