package br.com.cfc.exame;

import br.com.cfc.exame.ExameDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Exames")
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
public class ExameController {

    private final ExameService service;

    public ExameController(ExameService service) {
        this.service = service;
    }

    @GetMapping("/exames")
    public List<ExameResponse> listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                      @RequestParam(required = false) TipoExame tipo,
                                      @RequestParam(required = false) ResultadoExame resultado,
                                      @RequestParam(required = false) Long alunoId) {
        return service.listar(de, ate, tipo, resultado, alunoId);
    }

    @GetMapping("/processos/{id}/exames")
    public List<ExameResponse> doProcesso(@PathVariable Long id) {
        return service.doProcesso(id);
    }

    @PostMapping("/exames")
    @ResponseStatus(HttpStatus.CREATED)
    public ExameResponse agendar(@Valid @RequestBody ExameRequest req) {
        return service.agendar(req);
    }

    @PostMapping("/exames/{id}/resultado")
    public ExameResponse resultado(@PathVariable Long id, @Valid @RequestBody ResultadoRequest req) {
        return service.lancarResultado(id, req);
    }
}
