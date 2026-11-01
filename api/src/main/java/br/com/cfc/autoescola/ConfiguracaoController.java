package br.com.cfc.autoescola;

import br.com.cfc.autoescola.AutoescolaDtos.ConfiguracaoRequest;
import br.com.cfc.autoescola.AutoescolaDtos.ConfiguracaoResponse;
import br.com.cfc.autoescola.AutoescolaDtos.RegraCategoriaRequest;
import br.com.cfc.autoescola.AutoescolaDtos.RegraCategoriaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Configurações da autoescola")
@RestController
@RequestMapping("/api/configuracoes")
public class ConfiguracaoController {

    private final ConfiguracaoService service;

    public ConfiguracaoController(ConfiguracaoService service) {
        this.service = service;
    }

    @Operation(summary = "Regras operacionais (duração da aula, limites, cobrança de falta...). Todos da equipe podem ler.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA','INSTRUTOR','ALUNO')")
    public ConfiguracaoResponse obter() {
        return service.obter();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ConfiguracaoResponse atualizar(@Valid @RequestBody ConfiguracaoRequest req) {
        return service.atualizar(req);
    }

    @Operation(summary = "Carga horária mínima e idade por categoria")
    @GetMapping("/regras-categoria")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA','INSTRUTOR')")
    public List<RegraCategoriaResponse> listarRegras() {
        return service.listarRegras();
    }

    @PutMapping("/regras-categoria/{categoria}")
    @PreAuthorize("hasRole('ADMIN')")
    public RegraCategoriaResponse atualizarRegra(@PathVariable Categoria categoria,
                                                 @Valid @RequestBody RegraCategoriaRequest req) {
        return service.atualizarRegra(categoria, req);
    }
}
