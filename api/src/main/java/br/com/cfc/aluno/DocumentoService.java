package br.com.cfc.aluno;

import br.com.cfc.aluno.AlunoDtos.DocumentoResponse;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.domain.Tenants;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.SecurityUtils;
import br.com.cfc.shared.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

/** Upload de documentos do aluno (foto, RG, comprovante). Só JPEG, PNG e PDF, conferidos pelo conteúdo do arquivo. */
@Service
public class DocumentoService {

    static final int MAX_DOCUMENTOS_POR_ALUNO = 30;
    static final long MAX_BYTES = 10L * 1024 * 1024;

    public record Arquivo(String nome, String contentType, byte[] dados) {
    }

    private final DocumentoRepository repository;
    private final AlunoService alunoService;
    private final StorageService storage;
    private final AuditService auditService;

    public DocumentoService(DocumentoRepository repository, AlunoService alunoService, StorageService storage,
                            AuditService auditService) {
        this.repository = repository;
        this.alunoService = alunoService;
        this.storage = storage;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<DocumentoResponse> listar(Long alunoId) {
        alunoService.exigir(alunoId);
        return repository.findByAlunoIdOrderByCriadoEmDescIdDesc(alunoId).stream().map(DocumentoResponse::de).toList();
    }

    @Transactional
    public DocumentoResponse enviar(Long alunoId, TipoDocumento tipo, MultipartFile arquivo) {
        byte[] dados;
        try {
            dados = arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o arquivo enviado.", e);
        }
        return enviar(alunoId, tipo, arquivo.getOriginalFilename(), dados);
    }

    @Transactional
    public DocumentoResponse enviar(Long alunoId, TipoDocumento tipo, String nomeOriginal, byte[] dados) {
        Long autoescolaId = SecurityUtils.autoescolaIdObrigatorio();
        alunoService.exigir(alunoId);
        if (dados == null || dados.length == 0) {
            throw new RegraNegocioException("O arquivo está vazio.");
        }
        if (dados.length > MAX_BYTES) {
            throw new RegraNegocioException("O arquivo excede o limite de 10 MB.");
        }
        String contentType = detectarTipo(dados);
        if (contentType == null) {
            throw new RegraNegocioException("Formato não aceito. Envie uma imagem JPEG ou PNG, ou um PDF.");
        }
        if (repository.countByAlunoId(alunoId) >= MAX_DOCUMENTOS_POR_ALUNO) {
            throw new RegraNegocioException("Limite de " + MAX_DOCUMENTOS_POR_ALUNO + " documentos por aluno atingido.");
        }

        String caminho = autoescolaId + "/alunos/" + alunoId + "/" + UUID.randomUUID() + extensao(contentType);
        storage.salvar(caminho, dados);

        DocumentoAluno d = new DocumentoAluno();
        d.setAlunoId(alunoId);
        d.setTipo(tipo);
        d.setNomeOriginal(nomeSeguro(nomeOriginal, contentType));
        d.setContentType(contentType);
        d.setTamanho(dados.length);
        d.setCaminho(caminho);
        d = repository.save(d);
        var resposta = DocumentoResponse.de(d);
        auditService.registrar("documento_aluno", d.getId(), AcaoAuditoria.CRIACAO, null, resposta);
        return resposta;
    }

    @Transactional(readOnly = true)
    public Arquivo baixar(Long id) {
        DocumentoAluno d = exigir(id);
        return new Arquivo(d.getNomeOriginal(), d.getContentType(), storage.ler(d.getCaminho()));
    }

    @Transactional
    public void excluir(Long id) {
        DocumentoAluno d = exigir(id);
        var antes = DocumentoResponse.de(d);
        repository.delete(d);
        storage.apagar(d.getCaminho());
        auditService.registrar("documento_aluno", id, AcaoAuditoria.EXCLUSAO, antes, null);
    }

    private DocumentoAluno exigir(Long id) {
        return Tenants.daAutoescolaAtual(repository.findById(id), "Documento não encontrado.");
    }

    /** Descobre o tipo real pelos primeiros bytes. O Content-Type enviado pelo cliente não é confiável. */
    static String detectarTipo(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return "application/pdf";
        }
        return null;
    }

    private static String extensao(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".pdf";
        };
    }

    /** Nome só para exibição: sem pasta, sem caracteres de controle, tamanho limitado. */
    private static String nomeSeguro(String nome, String contentType) {
        String base = nome == null ? "" : nome.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (base.isEmpty()) {
            base = "documento" + extensao(contentType);
        }
        return base.length() > 150 ? base.substring(base.length() - 150) : base;
    }
}
