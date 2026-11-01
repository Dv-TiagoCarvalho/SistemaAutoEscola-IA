package br.com.cfc.shared.exception;

import org.springframework.http.HttpStatus;

public class NaoAutorizadoException extends ApiException {

    public NaoAutorizadoException(String mensagem) {
        super(HttpStatus.UNAUTHORIZED, "Não autenticado", mensagem);
    }
}
