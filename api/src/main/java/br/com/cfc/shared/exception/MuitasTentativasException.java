package br.com.cfc.shared.exception;

import org.springframework.http.HttpStatus;

public class MuitasTentativasException extends ApiException {

    public MuitasTentativasException(String mensagem) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas", mensagem);
    }
}
