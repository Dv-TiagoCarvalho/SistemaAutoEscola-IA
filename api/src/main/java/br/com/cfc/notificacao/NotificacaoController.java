package br.com.cfc.notificacao;

import br.com.cfc.notificacao.NotificacaoService.ContagemResponse;
import br.com.cfc.notificacao.NotificacaoService.NotificacaoResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Notificações")
@RestController
@RequestMapping("/api/notificacoes")
@PreAuthorize("hasAnyRole('ADMIN','SECRETARIA','INSTRUTOR','ALUNO')")
public class NotificacaoController {

    private final NotificacaoService service;

    public NotificacaoController(NotificacaoService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificacaoResponse> minhas() {
        return service.minhas();
    }

    @GetMapping("/nao-lidas")
    public ContagemResponse contar() {
        return service.contar();
    }

    @PostMapping("/{id}/leitura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ler(@PathVariable Long id) {
        service.marcarLida(id);
    }

    @PostMapping("/leitura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lerTodas() {
        service.marcarTodasLidas();
    }
}
