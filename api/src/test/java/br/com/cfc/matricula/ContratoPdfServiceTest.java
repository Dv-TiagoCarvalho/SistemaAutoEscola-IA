package br.com.cfc.matricula;

import br.com.cfc.autoescola.Categoria;
import br.com.cfc.matricula.Parcelamento.Parcela;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContratoPdfServiceTest {

    @Test
    void geraUmPdfValidoComAcentosEParcelas() {
        var parcelas = List.of(
                new Parcela(1, "Entrada", new BigDecimal("200.00"), LocalDate.of(2026, 11, 1)),
                new Parcela(2, "Parcela 1/2", new BigDecimal("450.00"), LocalDate.of(2026, 12, 5)));
        var dados = new ContratoPdfService.Dados(
                "CFC Exemplo", "11.222.333/0001-81", "São Paulo", "SP",
                "João da Conceição", "529.982.247-25", null, LocalDate.of(2000, 1, 15),
                "Rua das Flores, 10, Centro, São Paulo/SP", "11999990000",
                Categoria.B, "Pacote B completo", 20, 50, true,
                new BigDecimal("1200.00"), new BigDecimal("100.00"), new BigDecimal("1100.00"), LocalDate.of(2026, 11, 1),
                parcelas, 24, true, new BigDecimal("60.00"));

        byte[] pdf = new ContratoPdfService().gerar(dados);

        assertThat(pdf.length).isGreaterThan(1500);
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }
}
