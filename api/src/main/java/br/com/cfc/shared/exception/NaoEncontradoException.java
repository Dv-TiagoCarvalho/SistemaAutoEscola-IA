package br.com.cfc.shared.exception;

import org.springframework.http.HttpStatus;

public class NaoEncontradoException extends ApiException {

    public NaoEncontradoException(String mensagem) {
        super(HttpStatus.NOT_FOUND, "Não encontrado", mensagem);
    }
}
