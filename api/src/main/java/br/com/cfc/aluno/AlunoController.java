package br.com.cfc.aluno;

import br.com.cfc.aluno.AlunoDtos.AcessoRequest;
import br.com.cfc.aluno.AlunoDtos.AlunoRequest;
import br.com.cfc.aluno.AlunoDtos.AlunoResponse;
import br.com.cfc.aluno.AlunoDtos.AlunoResumo;
import br.com.cfc.aluno.AlunoDtos.AlunoStatusRequest;
import br.com.cfc.aluno.AlunoDtos.ConsentimentoRequest;
import br.com.cfc.aluno.AlunoDtos.ConsentimentoResponse;
import br.com.cfc.aluno.AlunoDtos.DocumentoResponse;
import br.com.cfc.aluno.AlunoDtos.ProcessoRequest;
import br.com.cfc.aluno.AlunoDtos.ProcessoResponse;
import br.com.cfc.aluno.AlunoDtos.ProcessoUpdateRequest;
import br.com.cfc.shared.web.PaginaResponse;
import br.com.cfc.usuario.UsuarioDtos.RedefinirSenhaRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "Alunos")
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
public class AlunoController {

    private final AlunoService alunoService;
    private final ProcessoService processoService;
    private final DocumentoService documentoService;
    private final ConsentimentoService consentimentoService;

    public AlunoController(AlunoService alunoService, ProcessoService processoService,
                           DocumentoService documentoService, ConsentimentoService consentimentoService) {
        this.alunoService = alunoService;
        this.processoService = processoService;
        this.documentoService = documentoService;
        this.consentimentoService = consentimentoService;
    }

    // ---------- Alunos ----------

    @Operation(summary = "Lista alunos. Busca por nome ou CPF; filtra pela situação do processo.")
    @GetMapping("/alunos")
    public PaginaResponse<AlunoResumo> listar(@RequestParam(required = false) String busca,
                                              @RequestParam(required = false) SituacaoProcesso situacao,
                                              @RequestParam(defaultValue = "false") boolean incluirInativos,
                                              @RequestParam(defaultValue = "0") int pagina,
                                              @RequestParam(defaultValue = "20") int tamanho) {
        return alunoService.listar(busca, situacao, incluirInativos, pagina, tamanho);
    }

    @PostMapping("/alunos")
    @ResponseStatus(HttpStatus.CREATED)
    public AlunoResponse criar(@Valid @RequestBody AlunoRequest req) {
        return alunoService.criar(req);
    }

    @GetMapping("/alunos/{id}")
    public AlunoResponse buscar(@PathVariable Long id) {
        return alunoService.buscar(id);
    }

    @PutMapping("/alunos/{id}")
    public AlunoResponse atualizar(@PathVariable Long id, @Valid @RequestBody AlunoRequest req) {
        return alunoService.atualizar(id, req);
    }

    @Operation(summary = "Ativa ou inativa o aluno (inativo não aparece na lista e perde o acesso ao app)")
    @PatchMapping("/alunos/{id}/status")
    public AlunoResponse alterarStatus(@PathVariable Long id, @Valid @RequestBody AlunoStatusRequest req) {
        return alunoService.alterarStatus(id, req.ativo());
    }

    @Operation(summary = "Cria o login do aluno no app")
    @PostMapping("/alunos/{id}/acesso")
    public AlunoResponse criarAcesso(@PathVariable Long id, @Valid @RequestBody AcessoRequest req) {
        return alunoService.criarAcesso(id, req);
    }

    @PutMapping("/alunos/{id}/acesso/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenhaDeAcesso(@PathVariable Long id, @Valid @RequestBody RedefinirSenhaRequest req) {
        alunoService.redefinirSenhaDeAcesso(id, req.novaSenha());
    }

    // ---------- Processos de habilitação ----------

    @GetMapping("/alunos/{id}/processos")
    public List<ProcessoResponse> listarProcessos(@PathVariable Long id) {
        return processoService.listar(id);
    }

    @PostMapping("/alunos/{id}/processos")
    @ResponseStatus(HttpStatus.CREATED)
    public ProcessoResponse criarProcesso(@PathVariable Long id, @Valid @RequestBody ProcessoRequest req) {
        return processoService.criar(id, req);
    }

    @PutMapping("/processos/{id}")
    public ProcessoResponse atualizarProcesso(@PathVariable Long id, @Valid @RequestBody ProcessoUpdateRequest req) {
        return processoService.atualizar(id, req);
    }

    // ---------- Documentos ----------

    @GetMapping("/alunos/{id}/documentos")
    public List<DocumentoResponse> listarDocumentos(@PathVariable Long id) {
        return documentoService.listar(id);
    }

    @Operation(summary = "Envia um documento (JPEG, PNG ou PDF, até 10 MB)")
    @PostMapping(value = "/alunos/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoResponse enviarDocumento(@PathVariable Long id,
                                             @RequestParam("tipo") TipoDocumento tipo,
                                             @RequestParam("arquivo") MultipartFile arquivo) {
        return documentoService.enviar(id, tipo, arquivo);
    }

    @GetMapping("/documentos/{id}/arquivo")
    public ResponseEntity<byte[]> baixarDocumento(@PathVariable Long id) {
        DocumentoService.Arquivo a = documentoService.baixar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(a.nome(), StandardCharsets.UTF_8).build().toString())
                .body(a.dados());
    }

    @DeleteMapping("/documentos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirDocumento(@PathVariable Long id) {
        documentoService.excluir(id);
    }

    // ---------- Consentimento LGPD ----------

    @GetMapping("/alunos/{id}/consentimentos")
    public List<ConsentimentoResponse> listarConsentimentos(@PathVariable Long id) {
        return consentimentoService.listar(id);
    }

    @PostMapping("/alunos/{id}/consentimentos")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsentimentoResponse registrarConsentimento(@PathVariable Long id,
                                                        @Valid @RequestBody ConsentimentoRequest req) {
        return consentimentoService.registrar(id, req.versaoTermo());
    }

    @PatchMapping("/consentimentos/{id}/revogacao")
    public ConsentimentoResponse revogarConsentimento(@PathVariable Long id) {
        return consentimentoService.revogar(id);
    }
}
