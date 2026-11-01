package br.com.cfc.agenda;

import br.com.cfc.agenda.AgendaDtos.InstrutorRequest;
import br.com.cfc.agenda.AgendaDtos.InstrutorResponse;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.domain.Tenants;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.shared.security.SecurityUtils;
import br.com.cfc.usuario.Usuario;
import br.com.cfc.usuario.UsuarioService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InstrutorService {

    private final InstrutorRepository repository;
    private final UsuarioService usuarioService;
    private final AuditService auditService;

    public InstrutorService(InstrutorRepository repository, UsuarioService usuarioService, AuditService auditService) {
        this.repository = repository;
        this.usuarioService = usuarioService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<InstrutorResponse> listar(boolean incluirInativos) {
        SecurityUtils.autoescolaIdObrigatorio();
        return repository.listar(incluirInativos).stream().map(InstrutorResponse::de).toList();
    }

    /** Se vier e-mail, cria também o acesso (perfil INSTRUTOR) para ele ver a própria agenda no app. */
    @Transactional
    public InstrutorResponse criar(InstrutorRequest req) {
        Long autoescolaId = SecurityUtils.autoescolaIdObrigatorio();
        Instrutor i = new Instrutor();
        preencher(i, req);
        boolean comAcesso = req.email() != null && !req.email().isBlank();
        if (comAcesso) {
            if (req.senha() == null || req.senha().isBlank()) {
                throw new RegraNegocioException("Informe a senha de acesso do instrutor.");
            }
            Usuario u = usuarioService.novoUsuario(autoescolaId, req.nome().trim(), req.email(), req.senha(), Perfil.INSTRUTOR);
            i.setUsuarioId(u.getId());
        }
        i = repository.save(i);
        var resposta = InstrutorResponse.de(i);
        auditService.registrar("instrutor", i.getId(), AcaoAuditoria.CRIACAO, null, resposta);
        return resposta;
    }

    @Transactional
    public InstrutorResponse atualizar(Long id, InstrutorRequest req) {
        Instrutor i = exigir(id);
        var antes = InstrutorResponse.de(i);
        preencher(i, req);
        var depois = InstrutorResponse.de(i);
        auditService.registrar("instrutor", id, AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    @Transactional
    public InstrutorResponse alterarStatus(Long id, boolean ativo) {
        Instrutor i = exigir(id);
        var antes = InstrutorResponse.de(i);
        i.setAtivo(ativo);
        if (i.getUsuarioId() != null) {
            usuarioService.definirAtivoDeInstrutor(i.getUsuarioId(), ativo);
        }
        var depois = InstrutorResponse.de(i);
        auditService.registrar("instrutor", id, AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    public Instrutor exigir(Long id) {
        return Tenants.daAutoescolaAtual(repository.findById(id), "Instrutor não encontrado.");
    }

    private void preencher(Instrutor i, InstrutorRequest req) {
        i.setNome(req.nome().trim());
        i.setTelefone(req.telefone() == null || req.telefone().isBlank() ? null : req.telefone().replaceAll("\\D", ""));
        i.setCnhValidade(req.cnhValidade());
        i.definirCategorias(req.categorias());
    }
}
