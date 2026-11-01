package br.com.cfc.usuario;

import br.com.cfc.shared.web.PaginaResponse;
import br.com.cfc.usuario.UsuarioDtos.AtualizarUsuarioRequest;
import br.com.cfc.usuario.UsuarioDtos.CriarUsuarioRequest;
import br.com.cfc.usuario.UsuarioDtos.RedefinirSenhaRequest;
import br.com.cfc.usuario.UsuarioDtos.UsuarioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Usuários da equipe")
@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @Operation(summary = "Lista a equipe da autoescola (admin, secretaria, instrutores)")
    @GetMapping
    public PaginaResponse<UsuarioResponse> listar(@RequestParam(required = false) String busca,
                                                  @RequestParam(defaultValue = "0") int pagina,
                                                  @RequestParam(defaultValue = "20") int tamanho) {
        return service.listar(busca, pagina, tamanho);
    }

    @GetMapping("/{id}")
    public UsuarioResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse criar(@Valid @RequestBody CriarUsuarioRequest req) {
        return service.criar(req);
    }

    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarUsuarioRequest req) {
        return service.atualizar(id, req);
    }

    @Operation(summary = "Define uma nova senha e encerra as sessões do usuário")
    @PutMapping("/{id}/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@PathVariable Long id, @Valid @RequestBody RedefinirSenhaRequest req) {
        service.redefinirSenha(id, req.novaSenha());
    }
}
