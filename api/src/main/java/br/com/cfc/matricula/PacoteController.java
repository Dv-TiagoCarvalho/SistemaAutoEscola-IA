package br.com.cfc.matricula;

import br.com.cfc.autoescola.Categoria;
import br.com.cfc.matricula.PacoteDtos.PacoteRequest;
import br.com.cfc.matricula.PacoteDtos.PacoteResponse;
import br.com.cfc.matricula.PacoteDtos.PacoteStatusRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Pacotes")
@RestController
@RequestMapping("/api/pacotes")
public class PacoteController {

    private final PacoteService service;

    public PacoteController(PacoteService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public List<PacoteResponse> listar(@RequestParam(required = false) Categoria categoria,
                                       @RequestParam(defaultValue = "false") boolean incluirInativos) {
        return service.listar(categoria, incluirInativos);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PacoteResponse criar(@Valid @RequestBody PacoteRequest req) {
        return service.criar(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PacoteResponse atualizar(@PathVariable Long id, @Valid @RequestBody PacoteRequest req) {
        return service.atualizar(id, req);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public PacoteResponse alterarStatus(@PathVariable Long id, @Valid @RequestBody PacoteStatusRequest req) {
        return service.alterarStatus(id, req.ativo());
    }
}
