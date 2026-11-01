package br.com.cfc.shared.exception;

import org.springframework.http.HttpStatus;

public class RegraNegocioException extends ApiException {

    public RegraNegocioException(String mensagem) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio", mensagem);
    }
}
