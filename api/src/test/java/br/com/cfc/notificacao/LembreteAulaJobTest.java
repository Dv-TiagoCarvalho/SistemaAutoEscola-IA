package br.com.cfc.notificacao;

import br.com.cfc.TestSupport;
import br.com.cfc.agenda.Aula;
import br.com.cfc.agenda.AulaRepository;
import br.com.cfc.agenda.Instrutor;
import br.com.cfc.agenda.InstrutorRepository;
import br.com.cfc.agenda.StatusAula;
import br.com.cfc.aluno.Aluno;
import br.com.cfc.aluno.AlunoRepository;
import br.com.cfc.autoescola.AutoescolaRepository;
import br.com.cfc.autoescola.ConfiguracaoAutoescola;
import br.com.cfc.autoescola.ConfiguracaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LembreteAulaJobTest {

    private final AulaRepository aulas = mock(AulaRepository.class);
    private final AlunoRepository alunos = mock(AlunoRepository.class);
    private final InstrutorRepository instrutores = mock(InstrutorRepository.class);
    private final ConfiguracaoRepository configs = mock(ConfiguracaoRepository.class);
    private final NotificacaoService notificacoes = mock(NotificacaoService.class);
    private final Instant agora = Instant.parse("2026-11-01T12:00:00Z");

    private LembreteAulaJob job() {
        return new LembreteAulaJob(mock(AutoescolaRepository.class), configs, aulas, alunos, instrutores, notificacoes,
                mock(PlatformTransactionManager.class), Clock.fixed(agora, ZoneOffset.UTC));
    }

    @Test
    void avisaAlunoEInstrutorUmaVezPorAula() {
        ConfiguracaoAutoescola cfg = new ConfiguracaoAutoescola();
        cfg.setLembreteHorasAntes(24);
        when(configs.findAll()).thenReturn(List.of(cfg));
        Aula a = new Aula();
        a.setId(9L);
        a.setAlunoId(30L);
        a.setInstrutorId(20L);
        a.setInicio(agora.plusSeconds(3600 * 5));
        when(aulas.buscar(eq(agora), eq(agora.plusSeconds(24 * 3600)), any(), any(), any(), eq(StatusAula.AGENDADA)))
                .thenReturn(List.of(a));
        Aluno aluno = new Aluno();
        aluno.setNome("Maria");
        aluno.setUsuarioId(100L);
        when(alunos.findById(30L)).thenReturn(Optional.of(aluno));
        Instrutor i = new Instrutor();
        i.setUsuarioId(200L);
        when(instrutores.findById(20L)).thenReturn(Optional.of(i));
        when(notificacoes.criarSeNova(any(), eq("aula-9"), any(), any())).thenReturn(true);

        int criadas = job().lembrarAulasDe(5L);

        assertThat(criadas).isEqualTo(2);
        verify(notificacoes).criarSeNova(eq(100L), eq("aula-9"), any(), any());
        verify(notificacoes).criarSeNova(eq(200L), eq("aula-9"), any(), any());
    }

    @Test
    void alunoSemAcessoNaoRecebeAviso() {
        ConfiguracaoAutoescola cfg = new ConfiguracaoAutoescola();
        when(configs.findAll()).thenReturn(List.of(cfg));
        Aula a = new Aula();
        a.setId(9L);
        a.setAlunoId(30L);
        a.setInstrutorId(20L);
        a.setInicio(agora.plusSeconds(60));
        when(aulas.buscar(any(), any(), any(), any(), any(), any())).thenReturn(List.of(a));
        when(alunos.findById(30L)).thenReturn(Optional.of(new Aluno()));
        when(instrutores.findById(20L)).thenReturn(Optional.of(new Instrutor()));

        assertThat(job().lembrarAulasDe(5L)).isZero();
        verify(notificacoes, never()).criarSeNova(any(), any(), any(), any());
    }
}
