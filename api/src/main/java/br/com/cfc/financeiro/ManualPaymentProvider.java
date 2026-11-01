package br.com.cfc.financeiro;

import org.springframework.stereotype.Component;

/** Recebimento manual: a secretaria confirma que o dinheiro entrou (caixa, maquininha, Pix na conta). */
@Component
public class ManualPaymentProvider implements PaymentProvider {

    @Override
    public String nome() {
        return "MANUAL";
    }

    @Override
    public ResultadoPagamento registrar(SolicitacaoPagamento solicitacao) {
        return new ResultadoPagamento(true, null, "Recebimento registrado manualmente.");
    }
}
