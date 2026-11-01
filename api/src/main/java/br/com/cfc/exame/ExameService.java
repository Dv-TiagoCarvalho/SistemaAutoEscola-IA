package br.com.cfc.exame;

import br.com.cfc.aluno.AlunoService;
import br.com.cfc.aluno.ProcessoHabilitacao;
import br.com.cfc.aluno.ProcessoService;
import br.com.cfc.aluno.SituacaoProcesso;
import br.com.cfc.autoescola.ConfiguracaoService;
import br.com.cfc.exame.ExameDtos.ExameRequest;
import br.com.cfc.exame.ExameDtos.ExameResponse;
import br.com.cfc.exame.ExameDtos.ResultadoRequest;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.domain.Tenants;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Exames teórico e prático. Regras:
 * <ul>
 *   <li>o exame prático só pode ser marcado depois da aprovação no teórico;</li>
 *   <li>não se marca outro exame do mesmo tipo enquanto houver um agendado, nem depois de aprovado;</li>
 *   <li>a tentativa é contada automaticamente (reprovações e faltas anteriores + 1);</li>
 *   <li>aprovação no exame prático encerra o processo como APROVADO.</li>
 * </ul>
 * Não há integração com Detran/RENACH: o resultado é lançado manualmente pela secretaria.
 */
@Service
public class ExameService {

    private static final java.util.Set<ResultadoExame> TENTATIVAS_GASTAS = EnumSet.of(ResultadoExame.REPROVADO, ResultadoExame.FALTOU);

    private final ExameRepository repository;
    private final ProcessoService processoService;
    private final AlunoService alunoService;
    private final ConfiguracaoService configuracaoService;
    private final AuditService auditService;
    private final Clock clock;

    public ExameService(ExameRepository repository, ProcessoService processoService, AlunoService alunoService,
                        ConfiguracaoService configuracaoService, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.processoService = processoService;
        this.alunoService = alunoService;
        this.configuracaoService = configuracaoService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ExameResponse> listar(LocalDate de, LocalDate ate, TipoExame tipo, ResultadoExame resultado, Long alunoId) {
        SecurityUtils.autoescolaIdObrigatorio();
        ZoneId zona = zona();
        LocalDate hoje = configuracaoService.hoje();
        LocalDate d = de == null ? hoje.minusDays(30) : de;
        LocalDate f = ate == null ? hoje.plusDays(90) : ate;
        return montar(repository.buscar(d.atStartOfDay(zona).toInstant(), f.plusDays(1).atStartOfDay(zona).toInstant(), tipo, resultado, alunoId));
    }

    @Transactional(readOnly = true)
    public List<ExameResponse> doProcesso(Long processoId) {
        processoService.exigir(processoId);
        return montar(repository.findByProcessoIdOrderByDataHoraDescIdDesc(processoId));
    }

    @Transactional
    public ExameResponse agendar(ExameRequest req) {
        SecurityUtils.autoescolaIdObrigatorio();
        ProcessoHabilitacao p = processoService.exigirComLock(req.processoId());
        if (p.getSituacao() == SituacaoProcesso.APROVADO || p.getSituacao() == SituacaoProcesso.DESISTENTE) {
            throw new RegraNegocioException("O processo está encerrado.");
        }
        Instant quando = req.dataHora().atZone(zona()).toInstant();
        if (quando.isBefore(clock.instant())) {
            throw new RegraNegocioException("A data do exame não pode estar no passado.");
        }
        if (repository.existsByProcessoIdAndTipoAndResultado(p.getId(), req.tipo(), ResultadoExame.AGENDADO)) {
            throw new ConflitoException("Já existe um exame " + nome(req.tipo()) + " agendado para este processo.");
        }
        if (repository.existsByProcessoIdAndTipoAndResultado(p.getId(), req.tipo(), ResultadoExame.APROVADO)) {
            throw new RegraNegocioException("O aluno já foi aprovado no exame " + nome(req.tipo()) + ".");
        }
        if (req.tipo() == TipoExame.PRATICO
                && !repository.existsByProcessoIdAndTipoAndResultado(p.getId(), TipoExame.TEORICO, ResultadoExame.APROVADO)) {
            throw new RegraNegocioException("O exame prático só pode ser marcado depois da aprovação no teórico.");
        }
        Exame e = new Exame();
        e.setAlunoId(p.getAlunoId());
        e.setProcessoId(p.getId());
        e.setTipo(req.tipo());
        e.setDataHora(quando);
        e.setLocal(req.local() == null || req.local().isBlank() ? null : req.local().trim());
        e.setTentativa((int) repository.countByProcessoIdAndTipoAndResultadoIn(p.getId(), req.tipo(), TENTATIVAS_GASTAS) + 1);
        e.setResultado(ResultadoExame.AGENDADO);
        e = repository.save(e);
        if (p.getSituacao() != SituacaoProcesso.AGUARDANDO_EXAME && req.tipo() == TipoExame.PRATICO) {
            p.setSituacao(SituacaoProcesso.AGUARDANDO_EXAME);
        }
        var r = montar(List.of(e)).get(0);
        auditService.registrar("exame", e.getId(), AcaoAuditoria.CRIACAO, null, r);
        return r;
    }

    @Transactional
    public ExameResponse lancarResultado(Long id, ResultadoRequest req) {
        SecurityUtils.autoescolaIdObrigatorio();
        Exame e = Tenants.daAutoescolaAtual(repository.findById(id), "Exame não encontrado.");
        if (e.getResultado() != ResultadoExame.AGENDADO) {
            throw new RegraNegocioException("Este exame já tem resultado (" + e.getResultado() + ").");
        }
        if (req.resultado() == ResultadoExame.AGENDADO) {
            throw new RegraNegocioException("Informe APROVADO, REPROVADO, FALTOU ou CANCELADO.");
        }
        if (req.resultado() != ResultadoExame.CANCELADO && e.getDataHora().isAfter(clock.instant())) {
            throw new RegraNegocioException("O exame ainda não aconteceu. Só dá para cancelar.");
        }
        var antes = montar(List.of(e)).get(0);
        e.setResultado(req.resultado());
        e.setObservacoes(req.observacoes() == null || req.observacoes().isBlank() ? null : req.observacoes().trim());

        ProcessoHabilitacao p = processoService.exigirComLock(e.getProcessoId());
        if (req.resultado() == ResultadoExame.APROVADO && e.getTipo() == TipoExame.PRATICO) {
            p.setSituacao(SituacaoProcesso.APROVADO);
        } else if (e.getTipo() == TipoExame.PRATICO && p.getSituacao() == SituacaoProcesso.AGUARDANDO_EXAME) {
            // reprovou, faltou ou cancelou: volta para as aulas
            p.setSituacao(SituacaoProcesso.EM_AULAS);
        }
        var depois = montar(List.of(e)).get(0);
        auditService.registrar("exame", id, AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    private List<ExameResponse> montar(List<Exame> exames) {
        if (exames.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nomes = alunoService.nomes(exames.stream().map(Exame::getAlunoId).distinct().toList());
        return exames.stream().map(e -> ExameResponse.de(e, nomes.get(e.getAlunoId()))).toList();
    }

    private ZoneId zona() {
        return ZoneId.of(configuracaoService.configuracaoAtual().getFusoHorario());
    }

    private static String nome(TipoExame t) {
        return t == TipoExame.TEORICO ? "teórico" : "prático";
    }
}
