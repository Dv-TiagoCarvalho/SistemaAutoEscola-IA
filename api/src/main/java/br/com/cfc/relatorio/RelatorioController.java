package br.com.cfc.relatorio;

import br.com.cfc.financeiro.StatusTitulo;
import br.com.cfc.relatorio.DashboardService.DashboardResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

@Tag(name = "Dashboard e relatórios")
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
public class RelatorioController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");

    private final DashboardService dashboard;
    private final RelatorioService relatorios;

    public RelatorioController(DashboardService dashboard, RelatorioService relatorios) {
        this.dashboard = dashboard;
        this.relatorios = relatorios;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return dashboard.obter();
    }

    @GetMapping("/relatorios/alunos.csv")
    public ResponseEntity<byte[]> alunos() {
        return arquivo(relatorios.alunosCsv(), CSV, "alunos.csv");
    }

    @GetMapping("/relatorios/titulos.csv")
    public ResponseEntity<byte[]> titulos(@RequestParam(required = false) StatusTitulo status,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return arquivo(relatorios.titulosCsv(status, de, ate), CSV, "titulos.csv");
    }

    @GetMapping("/relatorios/aulas.csv")
    public ResponseEntity<byte[]> aulas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return arquivo(relatorios.aulasCsv(de, ate), CSV, "aulas.csv");
    }

    @GetMapping("/relatorios/financeiro.pdf")
    public ResponseEntity<byte[]> financeiro(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes) {
        return arquivo(relatorios.financeiroPdf(mes), MediaType.APPLICATION_PDF, "financeiro.pdf");
    }

    private static ResponseEntity<byte[]> arquivo(byte[] conteudo, MediaType tipo, String nome) {
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .body(conteudo);
    }
}
