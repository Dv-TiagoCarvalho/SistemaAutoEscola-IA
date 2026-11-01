package br.com.cfc.agenda;

import br.com.cfc.agenda.AgendaDtos.ManutencaoRequest;
import br.com.cfc.agenda.AgendaDtos.ManutencaoResponse;
import br.com.cfc.agenda.AgendaDtos.VeiculoRequest;
import br.com.cfc.agenda.AgendaDtos.VeiculoResponse;
import br.com.cfc.autoescola.ConfiguracaoService;
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
    private final ManutencaoRepository manutencaoRepository;
    private final ConfiguracaoService configuracaoService;
    private final AuditService auditService;

    public VeiculoService(VeiculoRepository repository, ManutencaoRepository manutencaoRepository,
                          ConfiguracaoService configuracaoService, AuditService auditService) {
        this.repository = repository;
        this.manutencaoRepository = manutencaoRepository;
        this.configuracaoService = configuracaoService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<VeiculoResponse> listar(boolean incluirInativos) {
        SecurityUtils.autoescolaIdObrigatorio();
        var hoje = configuracaoService.hoje();
        return repository.listar(incluirInativos).stream().map(v -> VeiculoResponse.de(v, hoje)).toList();
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
        if (req.kmAtual() != null) {
            v.setKmAtual(req.kmAtual());
        }
        v.setLicenciamentoValidade(req.licenciamentoValidade());
        v.setSeguroValidade(req.seguroValidade());
        v.setProximaRevisaoKm(req.proximaRevisaoKm());
    }

    @Transactional(readOnly = true)
    public List<ManutencaoResponse> manutencoes(Long veiculoId) {
        exigir(veiculoId);
        return manutencaoRepository.findByVeiculoIdOrderByDataDescIdDesc(veiculoId).stream().map(ManutencaoResponse::de).toList();
    }

    /** Registra uma manutenção; se o km informado for maior que o atual, atualiza o hodômetro do veículo. */
    @Transactional
    public ManutencaoResponse registrarManutencao(Long veiculoId, ManutencaoRequest req) {
        Veiculo v = exigir(veiculoId);
        if (req.data().isAfter(configuracaoService.hoje())) {
            throw new br.com.cfc.shared.exception.RegraNegocioException("A data da manutenção não pode estar no futuro.");
        }
        ManutencaoVeiculo m = new ManutencaoVeiculo();
        m.setVeiculoId(veiculoId);
        m.setData(req.data());
        m.setDescricao(req.descricao().trim());
        m.setKm(req.km());
        m.setValor(req.valor());
        m = manutencaoRepository.save(m);
        if (req.km() != null && req.km() > v.getKmAtual()) {
            v.setKmAtual(req.km());
        }
        var r = ManutencaoResponse.de(m);
        auditService.registrar("manutencao_veiculo", m.getId(), AcaoAuditoria.CRIACAO, null, r);
        return r;
    }
}
