package br.com.cfc.relatorio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RelatorioServiceTest {

    @Test
    void escapaSeparadorAspasEQuebraDeLinha() {
        assertThat(RelatorioService.escapar("a;b")).isEqualTo("\"a;b\"");
        assertThat(RelatorioService.escapar("diz \"oi\"")).isEqualTo("\"diz \"\"oi\"\"\"");
        assertThat(RelatorioService.escapar("l1\nl2")).isEqualTo("\"l1\nl2\"");
        assertThat(RelatorioService.escapar(null)).isEmpty();
    }

    @Test
    void neutralizaFormulasDePlanilha() {
        assertThat(RelatorioService.escapar("=SOMA(A1)")).isEqualTo("'=SOMA(A1)");
        assertThat(RelatorioService.escapar("+55 11 99999-0000")).startsWith("'+55");
        assertThat(RelatorioService.escapar("@cmd")).isEqualTo("'@cmd");
    }

    @Test
    void valorSaiComVirgulaDecimal() {
        assertThat(RelatorioService.decimal(new BigDecimal("1234.5"))).isEqualTo("1234,50");
        assertThat(RelatorioService.decimal(null)).isEmpty();
    }

    @Test
    void cabecalhoTemBomESeparadorPontoEVirgula() {
        StringBuilder sb = RelatorioService.cabecalho("Nome", "CPF");
        RelatorioService.linha(sb, "Maria", "123");

        assertThat(sb.toString()).isEqualTo("﻿Nome;CPF\r\nMaria;123\r\n");
    }
}
