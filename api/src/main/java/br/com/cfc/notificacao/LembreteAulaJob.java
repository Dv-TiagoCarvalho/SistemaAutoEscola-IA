package br.com.cfc.notificacao;

import br.com.cfc.agenda.Aula;
import br.com.cfc.agenda.AulaRepository;
import br.com.cfc.agenda.Instrutor;
import br.com.cfc.agenda.InstrutorRepository;
import br.com.cfc.agenda.StatusAula;
import br.com.cfc.aluno.Aluno;
import br.com.cfc.aluno.AlunoRepository;
import br.com.cfc.autoescola.Autoescola;
import br.com.cfc.autoescola.AutoescolaRepository;
import br.com.cfc.autoescola.ConfiguracaoAutoescola;
import br.com.cfc.autoescola.ConfiguracaoRepository;
import br.com.cfc.shared.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * De hora em hora avisa aluno e instrutor das aulas que começam dentro de "lembrete_horas_antes" (configurável por
 * autoescola). Cada autoescola roda em sua própria transação e com seu próprio tenant; a chave "aula-ID" impede repetir o aviso.
 */
@Component
public class LembreteAulaJob {

    private static final Logger log = LoggerFactory.getLogger(LembreteAulaJob.class);

    private final AutoescolaRepository autoescolas;
    private final ConfiguracaoRepository configuracoes;
    private final AulaRepository aulas;
    private final AlunoRepository alunos;
    private final InstrutorRepository instrutores;
    private final NotificacaoService notificacoes;
    private final TransactionTemplate tx;
    private final Clock clock;

    public LembreteAulaJob(AutoescolaRepository autoescolas, ConfiguracaoRepository configuracoes, AulaRepository aulas,
                           AlunoRepository alunos, InstrutorRepository instrutores, NotificacaoService notificacoes,
                           PlatformTransactionManager tm, Clock clock) {
        this.autoescolas = autoescolas;
        this.configuracoes = configuracoes;
        this.aulas = aulas;
        this.alunos = alunos;
        this.instrutores = instrutores;
        this.notificacoes = notificacoes;
        this.tx = new TransactionTemplate(tm);
        this.clock = clock;
    }

    @Scheduled(cron = "0 5 * * * *")
    public void executar() {
        List<Long> ids;
        try {
            TenantContext.definirRaiz();
            ids = tx.execute(s -> autoescolas.findAll().stream().filter(Autoescola::isAtivo).map(Autoescola::getId).toList());
        } finally {
            TenantContext.limpar();
        }
        for (Long id : ids == null ? List.<Long>of() : ids) {
            try {
                TenantContext.definir(id);
                Integer criadas = tx.execute(s -> lembrarAulasDe(id));
                if (criadas != null && criadas > 0) {
                    log.info("Autoescola {}: {} lembretes de aula criados", id, criadas);
                }
            } catch (RuntimeException e) {
                log.error("Falha ao gerar lembretes da autoescola {}", id, e);
            } finally {
                TenantContext.limpar();
            }
        }
    }

    int lembrarAulasDe(Long autoescolaId) {
        ConfiguracaoAutoescola cfg = configuracoes.findAll().stream().findFirst().orElse(null);
        if (cfg == null) {
            return 0;
        }
        Instant agora = clock.instant();
        Instant ate = agora.plus(Duration.ofHours(cfg.getLembreteHorasAntes()));
        ZoneId zona = ZoneId.of(cfg.getFusoHorario());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
        int criadas = 0;
        for (Aula a : aulas.buscar(agora, ate, null, null, null, StatusAula.AGENDADA)) {
            String quando = fmt.format(a.getInicio().atZone(zona));
            String chave = "aula-" + a.getId();
            Aluno aluno = alunos.findById(a.getAlunoId()).orElse(null);
            if (aluno != null && aluno.getUsuarioId() != null
                    && notificacoes.criarSeNova(aluno.getUsuarioId(), chave, "Aula prática marcada", "Sua aula é em " + quando + ".")) {
                criadas++;
            }
            Instrutor instrutor = instrutores.findById(a.getInstrutorId()).orElse(null);
            if (instrutor != null && instrutor.getUsuarioId() != null) {
                String nome = aluno == null ? "aluno" : aluno.getNome();
                if (notificacoes.criarSeNova(instrutor.getUsuarioId(), chave, "Aula prática marcada", "Aula com " + nome + " em " + quando + ".")) {
                    criadas++;
                }
            }
        }
        return criadas;
    }
}
