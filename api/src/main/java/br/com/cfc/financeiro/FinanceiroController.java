package br.com.cfc.financeiro;

import br.com.cfc.financeiro.FinanceiroDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Tag(name = "Financeiro")
@RestController
@RequestMapping("/api")
public class FinanceiroController {

    private final FinanceiroService service;

    public FinanceiroController(FinanceiroService service) {
        this.service = service;
    }

    @GetMapping("/titulos")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public List<TituloResponse> listar(@RequestParam(required = false) Long alunoId,
                                       @RequestParam(required = false) StatusTitulo status,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                       @RequestParam(defaultValue = "false") boolean apenasVencidos) {
        return service.listar(alunoId, status, de, ate, apenasVencidos);
    }

    @GetMapping("/titulos/meus")
    @PreAuthorize("hasRole('ALUNO')")
    public List<TituloResponse> meus() {
        return service.meus();
    }

    @PostMapping("/titulos")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    @ResponseStatus(HttpStatus.CREATED)
    public TituloResponse criarAvulso(@Valid @RequestBody TituloAvulsoRequest req) {
        return service.criarAvulso(req);
    }

    @PostMapping("/titulos/{id}/baixa")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    @ResponseStatus(HttpStatus.CREATED)
    public PagamentoResponse baixar(@PathVariable Long id, @Valid @RequestBody BaixaRequest req) {
        return service.baixar(id, req);
    }

    @PostMapping("/titulos/{id}/cancelamento")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public TituloResponse cancelarTitulo(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
        return service.cancelarTitulo(id, req.motivo());
    }

    @PostMapping("/pagamentos/{id}/estorno")
    @PreAuthorize("hasRole('ADMIN')")
    public PagamentoResponse estornar(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
        return service.estornar(id, req.motivo());
    }

    @GetMapping("/pagamentos/{id}/recibo")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA','ALUNO')")
    public ResponseEntity<byte[]> recibo(@PathVariable Long id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("recibo-" + id + ".pdf").build().toString())
                .body(service.recibo(id));
    }

    @GetMapping("/despesas")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DespesaResponse> despesas(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
                                          @RequestParam(required = false) StatusDespesa status) {
        return service.listarDespesas(mes, status);
    }

    @PostMapping("/despesas")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public DespesaResponse criarDespesa(@Valid @RequestBody DespesaRequest req) {
        return service.criarDespesa(req);
    }

    @PostMapping("/despesas/{id}/pagamento")
    @PreAuthorize("hasRole('ADMIN')")
    public DespesaResponse pagarDespesa(@PathVariable Long id, @RequestBody(required = false) PagarDespesaRequest req) {
        return service.pagarDespesa(id, req == null ? null : req.dataPagamento());
    }

    @PostMapping("/despesas/{id}/cancelamento")
    @PreAuthorize("hasRole('ADMIN')")
    public DespesaResponse cancelarDespesa(@PathVariable Long id) {
        return service.cancelarDespesa(id);
    }

    @GetMapping("/financeiro/resumo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResumoFinanceiro resumo(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes) {
        return service.resumo(mes);
    }
}
