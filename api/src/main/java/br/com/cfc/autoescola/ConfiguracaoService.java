package br.com.cfc.autoescola;

import br.com.cfc.autoescola.AutoescolaDtos.ConfiguracaoRequest;
import br.com.cfc.autoescola.AutoescolaDtos.ConfiguracaoResponse;
import br.com.cfc.autoescola.AutoescolaDtos.RegraCategoriaRequest;
import br.com.cfc.autoescola.AutoescolaDtos.RegraCategoriaResponse;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.exception.NaoEncontradoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/** Configurações e regras por categoria da autoescola do usuário logado. */
@Service
public class ConfiguracaoService {

    private final ConfiguracaoRepository configuracaoRepository;
    private final RegraCategoriaRepository regraRepository;
    private final AuditService auditService;
    private final Clock clock;

    public ConfiguracaoService(ConfiguracaoRepository configuracaoRepository, RegraCategoriaRepository regraRepository,
                               AuditService auditService, Clock clock) {
        this.configuracaoRepository = configuracaoRepository;
        this.regraRepository = regraRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Para uso interno de outros módulos (agenda, simulados...). */
    @Transactional(readOnly = true)
    public ConfiguracaoAutoescola configuracaoAtual() {
        Long autoescolaId = SecurityUtils.autoescolaIdObrigatorio();
        return configuracaoRepository.findById(autoescolaId)
                .orElseThrow(() -> new NaoEncontradoException("Configurações da autoescola não encontradas."));
    }

    @Transactional(readOnly = true)
    public ConfiguracaoResponse obter() {
        return ConfiguracaoResponse.de(configuracaoAtual());
    }

    @Transactional
    public ConfiguracaoResponse atualizar(ConfiguracaoRequest req) {
        ConfiguracaoAutoescola config = configuracaoAtual();
        if (!config.getVersion().equals(req.version())) {
            throw new ConflitoException("As configurações foram alteradas por outra pessoa. Recarregue a tela.");
        }
        if (req.simuladoAcertosMinimos() > req.simuladoQtdQuestoes()) {
            throw new RegraNegocioException("Os acertos mínimos não podem ser maiores que o número de questões.");
        }
        if (req.cobrarFalta() && req.valorFalta().signum() == 0) {
            throw new RegraNegocioException("Informe o valor da falta ou desligue a cobrança.");
        }
        validarFuso(req.fusoHorario());

        var antes = ConfiguracaoResponse.de(config);
        config.setDuracaoAulaMin(req.duracaoAulaMin());
        config.setMaxAulasDiaAluno(req.maxAulasDiaAluno());
        config.setAntecedenciaCancelamentoHoras(req.antecedenciaCancelamentoHoras());
        config.setAntecedenciaAgendamentoHoras(req.antecedenciaAgendamentoHoras());
        config.setCobrarFalta(req.cobrarFalta());
        config.setValorFalta(req.valorFalta());
        config.setLembreteHorasAntes(req.lembreteHorasAntes());
        config.setSimuladoQtdQuestoes(req.simuladoQtdQuestoes());
        config.setSimuladoTempoMin(req.simuladoTempoMin());
        config.setSimuladoAcertosMinimos(req.simuladoAcertosMinimos());
        config.setFusoHorario(req.fusoHorario());
        config.setAtualizadoEm(clock.instant());
        configuracaoRepository.saveAndFlush(config); // flush para devolver a versão nova
        var depois = ConfiguracaoResponse.de(config);
        auditService.registrar("configuracao_autoescola", config.getAutoescolaId(), AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    @Transactional(readOnly = true)
    public List<RegraCategoriaResponse> listarRegras() {
        return regraRepository.findAllByOrderByCategoriaAsc().stream()
                .sorted(Comparator.comparing(RegraCategoria::getCategoria))
                .map(RegraCategoriaResponse::de)
                .toList();
    }

    @Transactional
    public RegraCategoriaResponse atualizarRegra(Categoria categoria, RegraCategoriaRequest req) {
        // Filtrado pelo tenant: só encontra a regra da própria autoescola.
        RegraCategoria regra = regraRepository.findByCategoria(categoria)
                .orElseThrow(() -> new NaoEncontradoException("Regra da categoria " + categoria + " não encontrada."));
        var antes = RegraCategoriaResponse.de(regra);
        regra.setHorasPraticasMinimas(req.horasPraticasMinimas());
        regra.setHorasTeoricasMinimas(req.horasTeoricasMinimas());
        regra.setIdadeMinima(req.idadeMinima());
        regra.setAtiva(req.ativa());
        var depois = RegraCategoriaResponse.de(regra);
        auditService.registrar("regra_categoria", regra.getId(), AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    private void validarFuso(String fuso) {
        try {
            ZoneId zona = ZoneId.of(fuso);
            if (!zona.getId().startsWith("America/") && !zona.getId().equals("UTC")) {
                throw new RegraNegocioException("Use um fuso horário do Brasil, por exemplo America/Sao_Paulo.");
            }
        } catch (DateTimeException e) {
            throw new RegraNegocioException("Fuso horário inválido.");
        }
    }
}
