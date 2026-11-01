package br.com.cfc.teorico;

import br.com.cfc.TestSupport;
import br.com.cfc.aluno.Aluno;
import br.com.cfc.aluno.AlunoRepository;
import br.com.cfc.aluno.AlunoService;
import br.com.cfc.autoescola.ConfiguracaoAutoescola;
import br.com.cfc.autoescola.ConfiguracaoService;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.teorico.TeoricoDtos.FinalizarSimuladoRequest;
import br.com.cfc.teorico.TeoricoDtos.SimuladoResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimuladoServiceTest {

    private final SimuladoRepository repo = mock(SimuladoRepository.class);
    private final SimuladoQuestaoRepository itens = mock(SimuladoQuestaoRepository.class);
    private final QuestaoRepository questoes = mock(QuestaoRepository.class);
    private final AlunoRepository alunos = mock(AlunoRepository.class);
    private final ConfiguracaoService config = mock(ConfiguracaoService.class);
    private final Instant inicio = Instant.parse("2026-11-01T12:00:00Z");
    private Clock relogio = Clock.fixed(inicio, ZoneOffset.UTC);
    private SimuladoService service;
    private final List<SimuladoQuestao> salvos = new ArrayList<>();
    private final List<Questao> banco = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TestSupport.logarComo(1L, 5L, Perfil.ALUNO);
        Aluno aluno = new Aluno();
        aluno.setId(30L);
        aluno.setAutoescolaId(5L);
        when(alunos.findByUsuarioId(1L)).thenReturn(Optional.of(aluno));
        ConfiguracaoAutoescola cfg = new ConfiguracaoAutoescola();
        cfg.setAutoescolaId(5L);
        cfg.setSimuladoQtdQuestoes(5);
        cfg.setSimuladoTempoMin(10);
        cfg.setSimuladoAcertosMinimos(4);
        when(config.configuracaoAtual()).thenReturn(cfg);
        for (long i = 1; i <= 8; i++) {
            Questao q = new Questao();
            q.setId(i);
            q.setAutoescolaId(5L);
            q.setTema("T");
            q.setEnunciado("Q" + i);
            q.setAltA("a");
            q.setAltB("b");
            q.setAltC("c");
            q.setAltD("d");
            q.setCorreta("B");
            banco.add(q);
        }
        when(questoes.findByAtivaTrue()).thenReturn(banco);
        when(questoes.findByIdIn(anyCollection())).thenReturn(banco);
        when(repo.findTop50ByAlunoIdOrderByIniciadoEmDescIdDesc(anyLong())).thenReturn(List.of());
        when(repo.save(any(Simulado.class))).thenAnswer(i -> {
            Simulado s = i.getArgument(0);
            s.setId(77L);
            s.setAutoescolaId(5L);
            return s;
        });
        when(itens.save(any(SimuladoQuestao.class))).thenAnswer(i -> {
            salvos.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(itens.findBySimuladoIdOrderByOrdem(77L)).thenReturn(salvos);
        service = new SimuladoService(repo, itens, questoes, alunos, mock(AlunoService.class), config, relogio);
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private Simulado aberto() {
        service.iniciar();
        Simulado s = new Simulado();
        s.setId(77L);
        s.setAutoescolaId(5L);
        s.setAlunoId(30L);
        s.setIniciadoEm(inicio);
        s.setLimiteEm(inicio.plusSeconds(600));
        s.setQtdQuestoes(5);
        s.setAcertosMinimos(4);
        when(repo.buscarComLock(77L)).thenReturn(Optional.of(s));
        return s;
    }

    private Map<Long, String> respostas(int certas) {
        Map<Long, String> r = new HashMap<>();
        int n = 0;
        for (SimuladoQuestao i : salvos) {
            r.put(i.getQuestaoId(), n++ < certas ? "B" : "A");
        }
        return r;
    }

    @Test
    void iniciaComQuantidadeConfiguradaSemRevelarGabarito() {
        SimuladoResponse r = service.iniciar();

        assertThat(r.questoes()).hasSize(5);
        assertThat(r.questoes()).allSatisfy(q -> {
            assertThat(q.correta()).isNull();
            assertThat(q.acertou()).isNull();
        });
        assertThat(r.limiteEm()).isEqualTo(inicio.plusSeconds(600));
        assertThat(r.acertosMinimos()).isEqualTo(4);
    }

    @Test
    void naoIniciaSemQuestoesSuficientes() {
        banco.subList(3, 8).clear();

        assertThatThrownBy(() -> service.iniciar()).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("3 questões ativas");
    }

    @Test
    void aprovaNoLimiteDeAcertosEReveleGabarito() {
        aberto();

        SimuladoResponse r = service.finalizar(77L, new FinalizarSimuladoRequest(respostas(4)));

        assertThat(r.acertos()).isEqualTo(4);
        assertThat(r.aprovado()).isTrue();
        assertThat(r.expirado()).isFalse();
        assertThat(r.questoes()).allSatisfy(q -> assertThat(q.correta()).isEqualTo("B"));
    }

    @Test
    void reprovaAbaixoDoMinimoEQuestaoSemRespostaContaComoErro() {
        aberto();
        Map<Long, String> r = respostas(3);
        r.remove(salvos.get(0).getQuestaoId());

        SimuladoResponse res = service.finalizar(77L, new FinalizarSimuladoRequest(r));

        assertThat(res.acertos()).isEqualTo(2);
        assertThat(res.aprovado()).isFalse();
    }

    @Test
    void reprovaPorExpiracaoMesmoComTodasCertas() {
        aberto();
        relogio = Clock.fixed(inicio.plusSeconds(600 + 31), ZoneOffset.UTC);
        service = new SimuladoService(repo, itens, questoes, alunos, mock(AlunoService.class), config, relogio);

        SimuladoResponse r = service.finalizar(77L, new FinalizarSimuladoRequest(respostas(5)));

        assertThat(r.expirado()).isTrue();
        assertThat(r.aprovado()).isFalse();
    }

    @Test
    void toleraAtrasoDeRedeDentroDe30Segundos() {
        aberto();
        relogio = Clock.fixed(inicio.plusSeconds(600 + 20), ZoneOffset.UTC);
        service = new SimuladoService(repo, itens, questoes, alunos, mock(AlunoService.class), config, relogio);

        assertThat(service.finalizar(77L, new FinalizarSimuladoRequest(respostas(5))).aprovado()).isTrue();
    }

    @Test
    void rejeitaFinalizarDuasVezesELetraInvalida() {
        aberto();
        assertThatThrownBy(() -> service.finalizar(77L, new FinalizarSimuladoRequest(respostaInvalida())))
                .isInstanceOf(RegraNegocioException.class);

        service.finalizar(77L, new FinalizarSimuladoRequest(respostas(5)));
        assertThatThrownBy(() -> service.finalizar(77L, new FinalizarSimuladoRequest(respostas(5))))
                .hasMessageContaining("já foi finalizado");
    }

    private Map<Long, String> respostaInvalida() {
        Map<Long, String> r = new HashMap<>();
        salvos.forEach(i -> r.put(i.getQuestaoId(), "Z"));
        return r;
    }

    @Test
    void apenasAlunoRealizaSimulado() {
        TestSupport.logarComo(2L, 5L, Perfil.SECRETARIA);

        assertThatThrownBy(() -> service.iniciar()).isInstanceOf(RegraNegocioException.class);
    }
}
