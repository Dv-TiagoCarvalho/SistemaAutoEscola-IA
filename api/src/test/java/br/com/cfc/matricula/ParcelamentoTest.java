package br.com.cfc.matricula;

import br.com.cfc.matricula.Parcelamento.Parcela;
import br.com.cfc.shared.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParcelamentoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 1);

    private static BigDecimal soma(List<Parcela> p) {
        return p.stream().map(Parcela::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void divideSemPerderCentavos() {
        var p = Parcelamento.calcular(new BigDecimal("1000.00"), null, HOJE, 3, LocalDate.of(2026, 12, 5));
        assertThat(p).extracting(Parcela::valor)
                .containsExactly(new BigDecimal("333.34"), new BigDecimal("333.33"), new BigDecimal("333.33"));
        assertThat(soma(p)).isEqualByComparingTo("1000.00");
    }

    @Test
    void entradaViraPrimeiraParcelaNaDataDaMatricula() {
        var p = Parcelamento.calcular(new BigDecimal("1000.00"), new BigDecimal("200.00"), HOJE, 4, LocalDate.of(2026, 12, 5));
        assertThat(p).hasSize(5);
        assertThat(p.get(0).descricao()).isEqualTo("Entrada");
        assertThat(p.get(0).vencimento()).isEqualTo(HOJE);
        assertThat(p.get(0).valor()).isEqualByComparingTo("200.00");
        assertThat(p.subList(1, 5)).extracting(Parcela::valor).allMatch(v -> v.compareTo(new BigDecimal("200.00")) == 0);
        assertThat(p.get(1).descricao()).isEqualTo("Parcela 1/4");
        assertThat(p.get(4).numero()).isEqualTo(5);
        assertThat(soma(p)).isEqualByComparingTo("1000.00");
    }

    @Test
    void vencimentosMensaisComDia31() {
        var p = Parcelamento.calcular(new BigDecimal("300.00"), null, HOJE, 3, LocalDate.of(2027, 1, 31));
        assertThat(p).extracting(Parcela::vencimento)
                .containsExactly(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31));
    }

    @Test
    void umaParcelaEhAVista() {
        var p = Parcelamento.calcular(new BigDecimal("850.50"), null, HOJE, 1, HOJE);
        assertThat(p).hasSize(1);
        assertThat(p.get(0).valor()).isEqualByComparingTo("850.50");
    }

    @Test
    void rejeitaEntradaIgualOuMaiorQueOTotal() {
        assertThatThrownBy(() -> Parcelamento.calcular(new BigDecimal("500.00"), new BigDecimal("500.00"), HOJE, 2, HOJE))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void rejeitaEntradasNegativasParcelasInvalidasECentavosDemais() {
        assertThatThrownBy(() -> Parcelamento.calcular(new BigDecimal("500.00"), new BigDecimal("-1"), HOJE, 2, HOJE))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> Parcelamento.calcular(new BigDecimal("500.00"), null, HOJE, 0, HOJE))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> Parcelamento.calcular(new BigDecimal("500.00"), null, HOJE, 49, HOJE))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> Parcelamento.calcular(new BigDecimal("500.005"), null, HOJE, 2, HOJE))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> Parcelamento.calcular(new BigDecimal("500.00"), null, HOJE, 2, null))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> Parcelamento.calcular(BigDecimal.ZERO, null, HOJE, 2, HOJE))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void rejeitaValorPequenoDemaisParaAsParcelas() {
        assertThatThrownBy(() -> Parcelamento.calcular(new BigDecimal("0.02"), null, HOJE, 3, HOJE))
                .isInstanceOf(RegraNegocioException.class);
    }
}
