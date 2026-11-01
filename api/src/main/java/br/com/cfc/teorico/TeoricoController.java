package br.com.cfc.teorico;

import br.com.cfc.teorico.TeoricoDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Teórico e simulados")
@RestController
@RequestMapping("/api")
public class TeoricoController {

    private final QuestaoService questoes;
    private final AulaTeoricaService aulas;
    private final SimuladoService simulados;

    public TeoricoController(QuestaoService questoes, AulaTeoricaService aulas, SimuladoService simulados) {
        this.questoes = questoes;
        this.aulas = aulas;
        this.simulados = simulados;
    }

    // ----- Banco de questões -----

    @GetMapping("/questoes")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public List<QuestaoResponse> listarQuestoes(@RequestParam(required = false) Boolean ativa,
                                                @RequestParam(required = false) String tema) {
        return questoes.listar(ativa, tema);
    }

    @PostMapping("/questoes")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestaoResponse criarQuestao(@Valid @RequestBody QuestaoRequest req) {
        return questoes.criar(req);
    }

    @PutMapping("/questoes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public QuestaoResponse atualizarQuestao(@PathVariable Long id, @Valid @RequestBody QuestaoRequest req) {
        return questoes.atualizar(id, req);
    }

    @PatchMapping("/questoes/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public QuestaoResponse statusQuestao(@PathVariable Long id, @RequestParam boolean ativa) {
        return questoes.alterarStatus(id, ativa);
    }

    @PostMapping(value = "/questoes/importacao", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public ImportacaoResponse importar(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        return questoes.importarCsv(new String(arquivo.getBytes(), StandardCharsets.UTF_8));
    }

    // ----- Aulas teóricas -----

    @GetMapping("/aulas-teoricas")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA','INSTRUTOR')")
    public List<AulaTeoricaResponse> listarAulasTeoricas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return aulas.listar(de, ate);
    }

    @PostMapping("/aulas-teoricas")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    @ResponseStatus(HttpStatus.CREATED)
    public AulaTeoricaResponse agendarAulaTeorica(@Valid @RequestBody AulaTeoricaRequest req) {
        return aulas.agendar(req);
    }

    @PostMapping("/aulas-teoricas/{id}/cancelamento")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public AulaTeoricaResponse cancelarAulaTeorica(@PathVariable Long id) {
        return aulas.cancelar(id);
    }

    @GetMapping("/aulas-teoricas/{id}/presencas")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA','INSTRUTOR')")
    public List<PresencaResponse> presencas(@PathVariable Long id) {
        return aulas.presencas(id);
    }

    @PutMapping("/aulas-teoricas/{id}/presencas")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA','INSTRUTOR')")
    public List<PresencaResponse> lancarPresenca(@PathVariable Long id, @Valid @RequestBody PresencaRequest req) {
        return aulas.registrarPresenca(id, req);
    }

    @GetMapping("/alunos/{id}/horas-teoricas")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public HorasTeoricasResponse horas(@PathVariable Long id) {
        return aulas.horas(id);
    }

    // ----- Simulados -----

    @PostMapping("/simulados")
    @PreAuthorize("hasRole('ALUNO')")
    @ResponseStatus(HttpStatus.CREATED)
    public SimuladoResponse iniciar() {
        return simulados.iniciar();
    }

    @PostMapping("/simulados/{id}/finalizacao")
    @PreAuthorize("hasRole('ALUNO')")
    public SimuladoResponse finalizar(@PathVariable Long id, @Valid @RequestBody FinalizarSimuladoRequest req) {
        return simulados.finalizar(id, req);
    }

    @GetMapping("/simulados/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA','ALUNO')")
    public SimuladoResponse buscarSimulado(@PathVariable Long id) {
        return simulados.buscar(id);
    }

    @GetMapping("/simulados/meus")
    @PreAuthorize("hasRole('ALUNO')")
    public DesempenhoResponse meuDesempenho() {
        return simulados.meuDesempenho();
    }

    @GetMapping("/alunos/{id}/simulados")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public DesempenhoResponse desempenho(@PathVariable Long id) {
        return simulados.desempenhoDoAluno(id);
    }
}
