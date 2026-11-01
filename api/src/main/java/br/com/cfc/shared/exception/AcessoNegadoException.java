package br.com.cfc.shared.exception;

import org.springframework.http.HttpStatus;

public class AcessoNegadoException extends ApiException {

    public AcessoNegadoException(String mensagem) {
        super(HttpStatus.FORBIDDEN, "Acesso negado", mensagem);
    }
}
