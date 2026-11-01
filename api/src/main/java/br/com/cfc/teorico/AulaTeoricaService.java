package br.com.cfc.teorico;

import br.com.cfc.aluno.AlunoService;
import br.com.cfc.autoescola.ConfiguracaoAutoescola;
import br.com.cfc.autoescola.ConfiguracaoService;
import br.com.cfc.autoescola.RegraCategoriaRepository;
import br.com.cfc.aluno.ProcessoHabilitacao;
import br.com.cfc.aluno.ProcessoRepository;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.domain.Tenants;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.SecurityUtils;
import br.com.cfc.teorico.TeoricoDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class AulaTeoricaService {

    private final AulaTeoricaRepository repository;
    private final PresencaRepository presencaRepository;
    private final AlunoService alunoService;
    private final ProcessoRepository processoRepository;
    private final RegraCategoriaRepository regraRepository;
    private final ConfiguracaoService configuracaoService;
    private final AuditService auditService;

    public AulaTeoricaService(AulaTeoricaRepository repository, PresencaRepository presencaRepository,
                              AlunoService alunoService, ProcessoRepository processoRepository,
                              RegraCategoriaRepository regraRepository, ConfiguracaoService configuracaoService,
                              AuditService auditService) {
        this.repository = repository;
        this.presencaRepository = presencaRepository;
        this.alunoService = alunoService;
        this.processoRepository = processoRepository;
        this.regraRepository = regraRepository;
        this.configuracaoService = configuracaoService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AulaTeoricaResponse> listar(LocalDate de, LocalDate ate) {
        ConfiguracaoAutoescola cfg = configuracaoService.configuracaoAtual();
        ZoneId zona = ZoneId.of(cfg.getFusoHorario());
        LocalDate d = de == null ? configuracaoService.hoje() : de;
        LocalDate f = ate == null ? d.plusDays(30) : ate;
        if (f.isBefore(d)) {
            throw new RegraNegocioException("A data final não pode ser anterior à inicial.");
        }
        return repository.buscar(d.atStartOfDay(zona).toInstant(), f.plusDays(1).atStartOfDay(zona).toInstant())
                .stream().map(AulaTeoricaResponse::de).toList();
    }

    @Transactional
    public AulaTeoricaResponse agendar(AulaTeoricaRequest req) {
        ZoneId zona = ZoneId.of(configuracaoService.configuracaoAtual().getFusoHorario());
        Instant inicio = req.inicio().atZone(zona).toInstant();
        Instant fim = req.fim().atZone(zona).toInstant();
        if (!fim.isAfter(inicio)) {
            throw new RegraNegocioException("O fim da aula deve ser depois do início.");
        }
        if (Duration.between(inicio, fim).toHours() > 8) {
            throw new RegraNegocioException("Uma aula teórica não pode passar de 8 horas.");
        }
        AulaTeorica a = new AulaTeorica();
        a.setTema(req.tema().trim());
        a.setInstrutorId(req.instrutorId());
        a.setInicio(inicio);
        a.setFim(fim);
        a.setLocal(req.local() == null || req.local().isBlank() ? null : req.local().trim());
        a = repository.save(a);
        auditService.registrar("aula_teorica", a.getId(), AcaoAuditoria.CRIACAO, null, AulaTeoricaResponse.de(a));
        return AulaTeoricaResponse.de(a);
    }

    @Transactional
    public AulaTeoricaResponse cancelar(Long id) {
        AulaTeorica a = exigir(id);
        if (a.getStatus() != StatusAulaTeorica.AGENDADA) {
            throw new RegraNegocioException("Só é possível cancelar aulas teóricas agendadas.");
        }
        var antes = AulaTeoricaResponse.de(a);
        a.setStatus(StatusAulaTeorica.CANCELADA);
        var depois = AulaTeoricaResponse.de(a);
        auditService.registrar("aula_teorica", id, AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    /** Lança a lista de presença e marca a aula como realizada. Pode ser reenviada para corrigir presenças. */
    @Transactional
    public List<PresencaResponse> registrarPresenca(Long id, PresencaRequest req) {
        AulaTeorica a = exigir(id);
        if (a.getStatus() == StatusAulaTeorica.CANCELADA) {
            throw new RegraNegocioException("Aula cancelada não aceita presença.");
        }
        var existentes = presencaRepository.findByAulaTeoricaId(id);
        for (PresencaRequest.Item item : req.itens()) {
            alunoService.exigir(item.alunoId());
            PresencaTeorica p = existentes.stream().filter(e -> e.getAlunoId().equals(item.alunoId())).findFirst()
                    .orElseGet(() -> {
                        PresencaTeorica n = new PresencaTeorica();
                        n.setAulaTeoricaId(id);
                        n.setAlunoId(item.alunoId());
                        return n;
                    });
            p.setPresente(item.presente());
            presencaRepository.save(p);
        }
        a.setStatus(StatusAulaTeorica.REALIZADA);
        auditService.registrar("aula_teorica", id, AcaoAuditoria.ALTERACAO, null, "Presença lançada: " + req.itens().size() + " alunos");
        return presencaRepository.findByAulaTeoricaId(id).stream()
                .map(p -> new PresencaResponse(p.getAlunoId(), p.isPresente())).toList();
    }

    @Transactional(readOnly = true)
    public List<PresencaResponse> presencas(Long id) {
        exigir(id);
        return presencaRepository.findByAulaTeoricaId(id).stream()
                .map(p -> new PresencaResponse(p.getAlunoId(), p.isPresente())).toList();
    }

    /** Horas teóricas assistidas versus o mínimo configurado da categoria do processo ativo do aluno. */
    @Transactional(readOnly = true)
    public HorasTeoricasResponse horas(Long alunoId) {
        alunoService.exigir(alunoId);
        SecurityUtils.autoescolaIdObrigatorio();
        long minutos = 0;
        for (Object[] par : repository.periodosDePresenca(alunoId)) {
            minutos += Duration.between((Instant) par[0], (Instant) par[1]).toMinutes();
        }
        BigDecimal horas = BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 1, RoundingMode.DOWN);
        BigDecimal minimo = processoRepository.findByAlunoIdOrderByCriadoEmDescIdDesc(alunoId).stream()
                .findFirst().map(ProcessoHabilitacao::getCategoria)
                .flatMap(regraRepository::findByCategoria)
                .map(r -> r.getHorasTeoricasMinimas()).orElse(null);
        return new HorasTeoricasResponse(alunoId, horas, minimo);
    }

    private AulaTeorica exigir(Long id) {
        return Tenants.daAutoescolaAtual(repository.findById(id), "Aula teórica não encontrada.");
    }
}
