package br.com.cfc.shared.web;

import org.springframework.data.domain.Page;

import java.util.List;

/** Formato estável de página para o app (não expõe a estrutura interna do Spring Data). */
public record PaginaResponse<T>(List<T> itens, int pagina, int tamanho, long totalItens, int totalPaginas) {

    public static <T> PaginaResponse<T> de(Page<T> page) {
        return new PaginaResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
