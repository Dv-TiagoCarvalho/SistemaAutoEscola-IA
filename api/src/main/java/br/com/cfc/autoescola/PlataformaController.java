package br.com.cfc.autoescola;

import br.com.cfc.autoescola.AutoescolaDtos.AtualizarAutoescolaRequest;
import br.com.cfc.autoescola.AutoescolaDtos.AutoescolaResponse;
import br.com.cfc.autoescola.AutoescolaDtos.CriarAutoescolaRequest;
import br.com.cfc.autoescola.AutoescolaDtos.StatusRequest;
import br.com.cfc.shared.web.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "Plataforma (SUPER_ADMIN)")
@RestController
@RequestMapping("/api/plataforma/autoescolas")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlataformaController {

    private final AutoescolaService service;

    public PlataformaController(AutoescolaService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaResponse<AutoescolaResponse> listar(@RequestParam(required = false) String busca,
                                                     @RequestParam(defaultValue = "0") int pagina,
                                                     @RequestParam(defaultValue = "20") int tamanho) {
        return service.listar(busca, pagina, tamanho);
    }

    @GetMapping("/{id}")
    public AutoescolaResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @Operation(summary = "Cadastra uma autoescola cliente com configurações padrão e o primeiro administrador")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AutoescolaResponse criar(@Valid @RequestBody CriarAutoescolaRequest req) {
        return service.criar(req);
    }

    @PutMapping("/{id}")
    public AutoescolaResponse atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarAutoescolaRequest req) {
        return service.atualizar(id, req);
    }

    @Operation(summary = "Ativa ou suspende a autoescola (suspensa = ninguém dela consegue entrar)")
    @PatchMapping("/{id}/status")
    public AutoescolaResponse alterarStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return service.alterarStatus(id, req.ativo());
    }
}
