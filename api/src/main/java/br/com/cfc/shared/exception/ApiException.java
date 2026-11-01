package br.com.cfc.shared.exception;

import org.springframework.http.HttpStatus;

/** Exceção de negócio que vira uma resposta HTTP com mensagem em português. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String titulo;

    protected ApiException(HttpStatus status, String titulo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.titulo = titulo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitulo() {
        return titulo;
    }
}
