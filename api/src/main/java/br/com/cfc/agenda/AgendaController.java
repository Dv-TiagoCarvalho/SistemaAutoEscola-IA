package br.com.cfc.agenda;

import br.com.cfc.agenda.AgendaDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Agenda")
@RestController
@RequestMapping("/api")
public class AgendaController {

    private final AulaService aulas;
    private final InstrutorService instrutores;
    private final VeiculoService veiculos;

    public AgendaController(AulaService aulas, InstrutorService instrutores, VeiculoService veiculos) {
        this.aulas = aulas;
        this.instrutores = instrutores;
        this.veiculos = veiculos;
    }

    // ----- Instrutores -----

    @GetMapping("/instrutores")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public List<InstrutorResponse> listarInstrutores(@RequestParam(defaultValue = "false") boolean incluirInativos) {
        return instrutores.listar(incluirInativos);
    }

    @PostMapping("/instrutores")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public InstrutorResponse criarInstrutor(@Valid @RequestBody InstrutorRequest req) {
        return instrutores.criar(req);
    }

    @PutMapping("/instrutores/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public InstrutorResponse atualizarInstrutor(@PathVariable Long id, @Valid @RequestBody InstrutorRequest req) {
        return instrutores.atualizar(id, req);
    }

    @PatchMapping("/instrutores/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public InstrutorResponse statusInstrutor(@PathVariable Long id, @Valid @RequestBody AtivoRequest req) {
        return instrutores.alterarStatus(id, req.ativo());
    }

    // ----- Veículos -----

    @GetMapping("/veiculos")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public List<VeiculoResponse> listarVeiculos(@RequestParam(defaultValue = "false") boolean incluirInativos) {
        return veiculos.listar(incluirInativos);
    }

    @PostMapping("/veiculos")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public VeiculoResponse criarVeiculo(@Valid @RequestBody VeiculoRequest req) {
        return veiculos.criar(req);
    }

    @PutMapping("/veiculos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public VeiculoResponse atualizarVeiculo(@PathVariable Long id, @Valid @RequestBody VeiculoRequest req) {
        return veiculos.atualizar(id, req);
    }

    @PatchMapping("/veiculos/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public VeiculoResponse statusVeiculo(@PathVariable Long id, @Valid @RequestBody AtivoRequest req) {
        return veiculos.alterarStatus(id, req.ativo());
    }

    // ----- Aulas -----

    @Operation(summary = "Agenda da autoescola. Instrutor e aluno enxergam só as próprias aulas.")
    @GetMapping("/aulas")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA','INSTRUTOR','ALUNO')")
    public List<AulaResponse> listarAulas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Long instrutorId,
            @RequestParam(required = false) Long alunoId,
            @RequestParam(required = false) Long veiculoId,
            @RequestParam(required = false) StatusAula status) {
        return aulas.listar(de, ate, instrutorId, alunoId, veiculoId, status);
    }

    @GetMapping("/aulas/saldo")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public SaldoAulasResponse saldo(@RequestParam Long matriculaId) {
        return aulas.saldo(matriculaId);
    }

    @PostMapping("/aulas")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    @ResponseStatus(HttpStatus.CREATED)
    public AulaResponse agendar(@Valid @RequestBody AulaRequest req) {
        return aulas.agendar(req);
    }

    @PostMapping("/aulas/{id}/cancelamento")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA','ALUNO')")
    public AulaResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelarAulaRequest req) {
        return aulas.cancelar(id, req);
    }

    @Operation(summary = "Registra a aula como realizada ou falta (instrutor da aula ou equipe)")
    @PostMapping("/aulas/{id}/registro")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA','INSTRUTOR')")
    public AulaResponse registrar(@PathVariable Long id, @Valid @RequestBody RegistroAulaRequest req) {
        return aulas.registrar(id, req);
    }
}
