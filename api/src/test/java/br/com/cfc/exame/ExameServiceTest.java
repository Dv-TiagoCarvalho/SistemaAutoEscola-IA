package br.com.cfc.exame;

import br.com.cfc.TestSupport;
import br.com.cfc.aluno.AlunoService;
import br.com.cfc.aluno.ProcessoHabilitacao;
import br.com.cfc.aluno.ProcessoService;
import br.com.cfc.aluno.SituacaoProcesso;
import br.com.cfc.autoescola.ConfiguracaoAutoescola;
import br.com.cfc.autoescola.ConfiguracaoService;
import br.com.cfc.exame.ExameDtos.ExameRequest;
import br.com.cfc.exame.ExameDtos.ResultadoRequest;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.Perfil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Relógio fixo: 2026-11-01 09:00 em São Paulo. */
class ExameServiceTest {

    private final ExameRepository repo = mock(ExameRepository.class);
    private final ProcessoService processos = mock(ProcessoService.class);
    private final AlunoService alunos = mock(AlunoService.class);
    private final ConfiguracaoService config = mock(ConfiguracaoService.class);
    private final TestSupport.RelogioFalso relogio = new TestSupport.RelogioFalso();
    private ExameService service;
    private ProcessoHabilitacao processo;
    private Exame salvo;

    @BeforeEach
    void setUp() {
        service = new ExameService(repo, processos, alunos, config, mock(AuditService.class), relogio);
        TestSupport.logarComo(1L, 5L, Perfil.SECRETARIA);
        ConfiguracaoAutoescola cfg = new ConfiguracaoAutoescola();
        cfg.setAutoescolaId(5L);
        when(config.configuracaoAtual()).thenReturn(cfg);
        when(config.hoje()).thenReturn(java.time.LocalDate.of(2026, 11, 1));
        when(alunos.nomes(anyCollection())).thenReturn(Map.of(30L, "Maria"));
        processo = new ProcessoHabilitacao();
        processo.setId(40L);
        processo.setAutoescolaId(5L);
        processo.setAlunoId(30L);
        processo.setSituacao(SituacaoProcesso.EM_AULAS);
        when(processos.exigirComLock(40L)).thenReturn(processo);
        when(repo.save(any())).thenAnswer(i -> {
            salvo = i.getArgument(0);
            salvo.setId(1L);
            salvo.setAutoescolaId(5L);
            when(repo.findById(1L)).thenReturn(Optional.of(salvo));
            return salvo;
        });
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private ExameRequest req(TipoExame tipo, LocalDateTime quando) {
        return new ExameRequest(40L, tipo, quando, " Detran ");
    }

    private static final LocalDateTime FUTURO = LocalDateTime.of(2026, 11, 20, 8, 0);

    @Test
    void praticoSoDepoisDeAprovadoNoTeorico() {
        assertThatThrownBy(() -> service.agendar(req(TipoExame.PRATICO, FUTURO)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("teórico");
        when(repo.existsByProcessoIdAndTipoAndResultado(40L, TipoExame.TEORICO, ResultadoExame.APROVADO)).thenReturn(true);
        var r = service.agendar(req(TipoExame.PRATICO, FUTURO));
        assertThat(r.local()).isEqualTo("Detran");
        assertThat(processo.getSituacao()).isEqualTo(SituacaoProcesso.AGUARDANDO_EXAME);
    }

    @Test
    void contaTentativasERejeitaDuplicadoEPassado() {
        when(repo.countByProcessoIdAndTipoAndResultadoIn(eq(40L), eq(TipoExame.TEORICO), any())).thenReturn(2L);
        assertThat(service.agendar(req(TipoExame.TEORICO, FUTURO)).tentativa()).isEqualTo(3);

        assertThatThrownBy(() -> service.agendar(req(TipoExame.TEORICO, LocalDateTime.of(2026, 10, 1, 8, 0))))
                .isInstanceOf(RegraNegocioException.class);
        when(repo.existsByProcessoIdAndTipoAndResultado(40L, TipoExame.TEORICO, ResultadoExame.AGENDADO)).thenReturn(true);
        assertThatThrownBy(() -> service.agendar(req(TipoExame.TEORICO, FUTURO))).isInstanceOf(ConflitoException.class);
    }

    @Test
    void aprovacaoNoPraticoEncerraOProcesso() {
        when(repo.existsByProcessoIdAndTipoAndResultado(40L, TipoExame.TEORICO, ResultadoExame.APROVADO)).thenReturn(true);
        service.agendar(req(TipoExame.PRATICO, FUTURO));
        relogio.avancar(java.time.Duration.ofDays(30));
        var r = service.lancarResultado(1L, new ResultadoRequest(ResultadoExame.APROVADO, null));
        assertThat(r.resultado()).isEqualTo(ResultadoExame.APROVADO);
        assertThat(processo.getSituacao()).isEqualTo(SituacaoProcesso.APROVADO);
        assertThatThrownBy(() -> service.lancarResultado(1L, new ResultadoRequest(ResultadoExame.REPROVADO, null)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void reprovacaoNoPraticoVoltaParaAsAulas() {
        when(repo.existsByProcessoIdAndTipoAndResultado(40L, TipoExame.TEORICO, ResultadoExame.APROVADO)).thenReturn(true);
        service.agendar(req(TipoExame.PRATICO, FUTURO));
        relogio.avancar(java.time.Duration.ofDays(30));
        service.lancarResultado(1L, new ResultadoRequest(ResultadoExame.REPROVADO, "Bateu na baliza"));
        assertThat(processo.getSituacao()).isEqualTo(SituacaoProcesso.EM_AULAS);
    }

    @Test
    void naoLancaResultadoDeExameFuturoMasPodeCancelar() {
        service.agendar(req(TipoExame.TEORICO, FUTURO));
        assertThatThrownBy(() -> service.lancarResultado(1L, new ResultadoRequest(ResultadoExame.APROVADO, null)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("ainda não aconteceu");
        assertThat(service.lancarResultado(1L, new ResultadoRequest(ResultadoExame.CANCELADO, null)).resultado())
                .isEqualTo(ResultadoExame.CANCELADO);
    }

    @Test
    void processoEncerradoNaoAgenda() {
        processo.setSituacao(SituacaoProcesso.DESISTENTE);
        assertThatThrownBy(() -> service.agendar(req(TipoExame.TEORICO, FUTURO))).isInstanceOf(RegraNegocioException.class);
    }
}
