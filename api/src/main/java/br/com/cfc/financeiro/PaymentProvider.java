package br.com.cfc.financeiro;

import java.math.BigDecimal;

/**
 * Ponto de integração com meios de pagamento (Pix, boleto, cartão, Asaas, Mercado Pago...).
 * Hoje só existe o {@link ManualPaymentProvider}, que registra o que a secretaria recebeu no balcão.
 * Para integrar um gateway, crie outra implementação @Component e troque a injeção no FinanceiroService
 * (ou escolha por configuração da autoescola).
 */
public interface PaymentProvider {

    String nome();

    ResultadoPagamento registrar(SolicitacaoPagamento solicitacao);

    record SolicitacaoPagamento(Long tituloId, BigDecimal valor, FormaPagamento forma) {
    }

    record ResultadoPagamento(boolean confirmado, String referenciaExterna, String mensagem) {
    }
}
