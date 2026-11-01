package br.com.cfc.shared.web;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestInfo {

    private RequestInfo() {
    }

    /** IP de quem fez a requisição atual (nulo fora de uma requisição). */
    public static String ipAtual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
