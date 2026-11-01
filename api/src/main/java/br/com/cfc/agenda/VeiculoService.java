package br.com.cfc.agenda;

import br.com.cfc.agenda.AgendaDtos.VeiculoRequest;
import br.com.cfc.agenda.AgendaDtos.VeiculoResponse;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.domain.Tenants;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VeiculoService {

    private final VeiculoRepository repository;
    private final AuditService auditService;

    public VeiculoService(VeiculoRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<VeiculoResponse> listar(boolean incluirInativos) {
        SecurityUtils.autoescolaIdObrigatorio();
        return repository.listar(incluirInativos).stream().map(VeiculoResponse::de).toList();
    }

    @Transactional
    public VeiculoResponse criar(VeiculoRequest req) {
        SecurityUtils.autoescolaIdObrigatorio();
        String placa = normalizarPlaca(req.placa());
        if (repository.existsByPlaca(placa)) {
            throw new ConflitoException("Já existe um veículo com esta placa.");
        }
        Veiculo v = new Veiculo();
        v.setPlaca(placa);
        preencher(v, req);
        v = repository.save(v);
        var resposta = VeiculoResponse.de(v);
        auditService.registrar("veiculo", v.getId(), AcaoAuditoria.CRIACAO, null, resposta);
        return resposta;
    }

    @Transactional
    public VeiculoResponse atualizar(Long id, VeiculoRequest req) {
        Veiculo v = exigir(id);
        String placa = normalizarPlaca(req.placa());
        if (!placa.equals(v.getPlaca()) && repository.existsByPlaca(placa)) {
            throw new ConflitoException("Já existe um veículo com esta placa.");
        }
        var antes = VeiculoResponse.de(v);
        v.setPlaca(placa);
        preencher(v, req);
        var depois = VeiculoResponse.de(v);
        auditService.registrar("veiculo", id, AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    @Transactional
    public VeiculoResponse alterarStatus(Long id, boolean ativo) {
        Veiculo v = exigir(id);
        var antes = VeiculoResponse.de(v);
        v.setAtivo(ativo);
        var depois = VeiculoResponse.de(v);
        auditService.registrar("veiculo", id, AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    public Veiculo exigir(Long id) {
        return Tenants.daAutoescolaAtual(repository.findById(id), "Veículo não encontrado.");
    }

    static String normalizarPlaca(String placa) {
        return placa.replace("-", "").trim().toUpperCase();
    }

    private void preencher(Veiculo v, VeiculoRequest req) {
        v.setModelo(req.modelo().trim());
        v.setAno(req.ano());
        v.setCategoria(req.categoria());
    }
}
