package br.com.cfc.shared.exception;

import org.springframework.http.HttpStatus;

public class ConflitoException extends ApiException {

    public ConflitoException(String mensagem) {
        super(HttpStatus.CONFLICT, "Conflito", mensagem);
    }
}
