package br.com.cfc.matricula;

import br.com.cfc.autoescola.Categoria;
import br.com.cfc.matricula.PacoteDtos.PacoteRequest;
import br.com.cfc.matricula.PacoteDtos.PacoteResponse;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.domain.Tenants;
import br.com.cfc.shared.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PacoteService {

    private final PacoteRepository repository;
    private final AuditService auditService;

    public PacoteService(PacoteRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PacoteResponse> listar(Categoria categoria, boolean incluirInativos) {
        SecurityUtils.autoescolaIdObrigatorio();
        return repository.buscar(categoria, incluirInativos).stream().map(PacoteResponse::de).toList();
    }

    @Transactional
    public PacoteResponse criar(PacoteRequest req) {
        SecurityUtils.autoescolaIdObrigatorio();
        Pacote p = new Pacote();
        preencher(p, req);
        p.setAtivo(true);
        p = repository.save(p);
        var resposta = PacoteResponse.de(p);
        auditService.registrar("pacote", p.getId(), AcaoAuditoria.CRIACAO, null, resposta);
        return resposta;
    }

    /** Alterar o pacote não muda matrículas já feitas: elas guardam uma cópia dos dados. */
    @Transactional
    public PacoteResponse atualizar(Long id, PacoteRequest req) {
        Pacote p = exigir(id);
        var antes = PacoteResponse.de(p);
        preencher(p, req);
        var depois = PacoteResponse.de(p);
        auditService.registrar("pacote", id, AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    @Transactional
    public PacoteResponse alterarStatus(Long id, boolean ativo) {
        Pacote p = exigir(id);
        var antes = PacoteResponse.de(p);
        p.setAtivo(ativo);
        var depois = PacoteResponse.de(p);
        auditService.registrar("pacote", id, AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    public Pacote exigir(Long id) {
        return Tenants.daAutoescolaAtual(repository.findById(id), "Pacote não encontrado.");
    }

    private void preencher(Pacote p, PacoteRequest req) {
        p.setCategoria(req.categoria());
        p.setNome(req.nome().trim());
        p.setQtdAulasPraticas(req.qtdAulasPraticas());
        p.setTeoricoIncluso(req.teoricoIncluso());
        p.setPreco(req.preco());
    }
}
