package br.com.cfc.matricula;

import br.com.cfc.matricula.MatriculaDtos.AulaAvulsaRequest;
import br.com.cfc.matricula.MatriculaDtos.CancelamentoRequest;
import br.com.cfc.matricula.MatriculaDtos.MatriculaDetalheResponse;
import br.com.cfc.matricula.MatriculaDtos.MatriculaRequest;
import br.com.cfc.matricula.MatriculaDtos.MatriculaResponse;
import br.com.cfc.shared.web.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@Tag(name = "Matrículas")
@RestController
@RequestMapping("/api/matriculas")
@PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
public class MatriculaController {

    private final MatriculaService service;

    public MatriculaController(MatriculaService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaResponse<MatriculaResponse> listar(@RequestParam(required = false) Long alunoId,
                                                    @RequestParam(required = false) StatusMatricula status,
                                                    @RequestParam(defaultValue = "0") int pagina,
                                                    @RequestParam(defaultValue = "20") int tamanho) {
        return service.listar(alunoId, status, pagina, tamanho);
    }

    @Operation(summary = "Matricula o aluno em um pacote: cria os créditos de aula, as parcelas e o contrato em PDF")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatriculaDetalheResponse criar(@Valid @RequestBody MatriculaRequest req) {
        return service.criar(req);
    }

    @GetMapping("/{id}")
    public MatriculaDetalheResponse detalhar(@PathVariable Long id) {
        return service.detalhar(id);
    }

    @PostMapping("/{id}/aulas-avulsas")
    @ResponseStatus(HttpStatus.CREATED)
    public MatriculaDetalheResponse adicionarAulasAvulsas(@PathVariable Long id,
                                                          @Valid @RequestBody AulaAvulsaRequest req) {
        return service.adicionarAulasAvulsas(id, req);
    }

    @PostMapping("/{id}/cancelamento")
    public MatriculaDetalheResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelamentoRequest req) {
        return service.cancelar(id, req.motivo());
    }

    @GetMapping("/{id}/contrato")
    public ResponseEntity<byte[]> contrato(@PathVariable Long id) {
        byte[] pdf = service.contrato(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("contrato-" + id + ".pdf", StandardCharsets.UTF_8).build().toString())
                .body(pdf);
    }
}
