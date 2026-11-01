package br.com.cfc.agenda;

import br.com.cfc.TestSupport;
import br.com.cfc.agenda.AgendaDtos.AulaRequest;
import br.com.cfc.agenda.AgendaDtos.CancelarAulaRequest;
import br.com.cfc.agenda.AgendaDtos.RegistroAulaRequest;
import br.com.cfc.aluno.Aluno;
import br.com.cfc.aluno.AlunoRepository;
import br.com.cfc.aluno.AlunoService;
import br.com.cfc.aluno.ProcessoHabilitacao;
import br.com.cfc.aluno.ProcessoService;
import br.com.cfc.aluno.SituacaoProcesso;
import br.com.cfc.aluno.StatusExame;
import br.com.cfc.autoescola.Categoria;
import br.com.cfc.autoescola.ConfiguracaoAutoescola;
import br.com.cfc.autoescola.ConfiguracaoService;
import br.com.cfc.financeiro.OrigemTitulo;
import br.com.cfc.financeiro.TituloReceber;
import br.com.cfc.financeiro.TituloReceberRepository;
import br.com.cfc.matricula.CreditoAula;
import br.com.cfc.matricula.CreditoAulaRepository;
import br.com.cfc.matricula.Matricula;
import br.com.cfc.matricula.MatriculaRepository;
import br.com.cfc.matricula.StatusMatricula;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.exception.NaoEncontradoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.Perfil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Relógio fixo: 2026-11-01 12:00 UTC = 09:00 em São Paulo. */
class AulaServiceTest {

    private static final LocalDateTime AULA = LocalDateTime.of(2026, 11, 5, 14, 0);

    private final AulaRepository repo = mock(AulaRepository.class);
    private final MatriculaRepository matriculas = mock(MatriculaRepository.class);
    private final CreditoAulaRepository creditos = mock(CreditoAulaRepository.class);
    private final TituloReceberRepository titulos = mock(TituloReceberRepository.class);
    private final InstrutorRepository instrutores = mock(InstrutorRepository.class);
    private final VeiculoRepository veiculos = mock(VeiculoRepository.class);
    private final AlunoRepository alunosRepo = mock(AlunoRepository.class);
    private final AlunoService alunos = mock(AlunoService.class);
    private final ProcessoService processos = mock(ProcessoService.class);
    private final ConfiguracaoService config = mock(ConfiguracaoService.class);
    private final TestSupport.RelogioFalso relogio = new TestSupport.RelogioFalso();
    private AulaService service;

    private ConfiguracaoAutoescola cfg;
    private Matricula matricula;
    private Instrutor instrutor;
    private Veiculo veiculo;
    private ProcessoHabilitacao processo;
    private Aluno aluno;

    @BeforeEach
    void setUp() {
        service = new AulaService(repo, matriculas, creditos, titulos, instrutores, veiculos, alunosRepo, alunos,
                processos, config, mock(AuditService.class), relogio);
        TestSupport.logarComo(1L, 5L, Perfil.SECRETARIA);

        cfg = new ConfiguracaoAutoescola();
        cfg.setAutoescolaId(5L);
        when(config.configuracaoAtual()).thenReturn(cfg);
        when(config.hoje()).thenReturn(LocalDate.of(2026, 11, 1));

        aluno = new Aluno();
        aluno.setId(30L);
        aluno.setAutoescolaId(5L);
        aluno.setNome("Maria");
        aluno.setAtivo(true);
        aluno.setUsuarioId(99L);
        when(alunos.exigir(30L)).thenReturn(aluno);
        when(alunos.nomes(anyCollection())).thenReturn(Map.of(30L, "Maria"));

        processo = new ProcessoHabilitacao();
        processo.setId(40L);
        processo.setAutoescolaId(5L);
        processo.setCategoria(Categoria.B);
        processo.setSituacao(SituacaoProcesso.MATRICULADO);
        processo.setExameMedico(StatusExame.APTO);
        processo.setExamePsicologico(StatusExame.APTO);
        when(processos.exigir(40L)).thenReturn(processo);
        when(processos.exigirComLock(40L)).thenReturn(processo);

        matricula = new Matricula();
        matricula.setId(60L);
        matricula.setAutoescolaId(5L);
        matricula.setAlunoId(30L);
        matricula.setProcessoId(40L);
        matricula.setStatus(StatusMatricula.ATIVA);
        when(matriculas.buscarComLock(60L)).thenReturn(Optional.of(matricula));
        when(matriculas.findById(60L)).thenReturn(Optional.of(matricula));

        instrutor = new Instrutor();
        instrutor.setId(70L);
        instrutor.setAutoescolaId(5L);
        instrutor.setNome("Carlos");
        instrutor.setUsuarioId(88L);
        instrutor.definirCategorias(Set.of(Categoria.B));
        when(instrutores.buscarComLock(70L)).thenReturn(Optional.of(instrutor));
        when(instrutores.findByIdIn(anyCollection())).thenReturn(List.of(instrutor));

        veiculo = new Veiculo();
        veiculo.setId(80L);
        veiculo.setAutoescolaId(5L);
        veiculo.setPlaca("ABC1D23");
        veiculo.setCategoria(Categoria.B);
        when(veiculos.buscarComLock(80L)).thenReturn(Optional.of(veiculo));
        when(veiculos.findByIdIn(anyCollection())).thenReturn(List.of(veiculo));

        CreditoAula c = new CreditoAula();
        c.setQuantidade(2);
        when(creditos.findByMatriculaIdOrderByIdAsc(60L)).thenReturn(List.of(c));

        when(repo.save(any())).thenAnswer(i -> {
            Aula a = i.getArgument(0);
            a.setId(1L);
            return a;
        });
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private AulaRequest req(LocalDateTime inicio) {
        return new AulaRequest(60L, 70L, 80L, inicio);
    }

    private Aula aula(Instant inicio) {
        Aula a = new Aula();
        a.setId(1L);
        a.setAutoescolaId(5L);
        a.setAlunoId(30L);
        a.setMatriculaId(60L);
        a.setInstrutorId(70L);
        a.setVeiculoId(80L);
        a.setInicio(inicio);
        a.setFim(inicio.plusSeconds(3000));
        a.setStatus(StatusAula.AGENDADA);
        when(repo.findById(1L)).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void agendaConvertendoHorarioLocalParaUtcEUsandoADuracaoConfigurada() {
        var r = service.agendar(req(AULA));
        assertThat(r.inicio()).isEqualTo(Instant.parse("2026-11-05T17:00:00Z"));
        assertThat(r.fim()).isEqualTo(Instant.parse("2026-11-05T17:50:00Z"));
        assertThat(r.status()).isEqualTo(StatusAula.AGENDADA);
        assertThat(r.veiculoPlaca()).isEqualTo("ABC1D23");
    }

    @Test
    void exigeAntecedenciaMinima() {
        // agora = 09:00 local; antecedência padrão = 2 h
        assertThatThrownBy(() -> service.agendar(req(LocalDateTime.of(2026, 11, 1, 10, 30))))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("antecedência");
        assertThat(service.agendar(req(LocalDateTime.of(2026, 11, 1, 11, 0))).id()).isEqualTo(1L);
    }

    @Test
    void rejeitaConflitosDeInstrutorVeiculoEAluno() {
        when(repo.conflitoInstrutor(eq(70L), any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.agendar(req(AULA))).isInstanceOf(ConflitoException.class).hasMessageContaining("instrutor");
        when(repo.conflitoInstrutor(eq(70L), any(), any(), any())).thenReturn(false);

        when(repo.conflitoVeiculo(eq(80L), any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.agendar(req(AULA))).isInstanceOf(ConflitoException.class).hasMessageContaining("veículo");
        when(repo.conflitoVeiculo(eq(80L), any(), any(), any())).thenReturn(false);

        when(repo.conflitoAluno(eq(30L), any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.agendar(req(AULA))).isInstanceOf(ConflitoException.class).hasMessageContaining("aluno");
        verify(repo, never()).save(any());
    }

    @Test
    void semSaldoDeAulasNaoAgenda() {
        when(repo.contarDaMatricula(eq(60L), anyCollection())).thenReturn(2L);
        assertThatThrownBy(() -> service.agendar(req(AULA)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("aulas disponíveis");
    }

    @Test
    void respeitaLimiteDiarioDoAluno() {
        when(repo.contarDoAlunoNoPeriodo(eq(30L), any(), any(), any())).thenReturn(2L);
        assertThatThrownBy(() -> service.agendar(req(AULA)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("por dia");
    }

    @Test
    void examesPendentesBloqueiamSeAConfiguracaoExigir() {
        processo.setExamePsicologico(StatusExame.PENDENTE);
        assertThatThrownBy(() -> service.agendar(req(AULA))).isInstanceOf(RegraNegocioException.class).hasMessageContaining("exames");
        cfg.setExigirExamesParaAulas(false);
        assertThat(service.agendar(req(AULA)).id()).isEqualTo(1L);
    }

    @Test
    void ladvVencidaNaDataDaAula() {
        processo.setLadvValidade(LocalDate.of(2026, 11, 4));
        assertThatThrownBy(() -> service.agendar(req(AULA))).isInstanceOf(RegraNegocioException.class).hasMessageContaining("LADV");
    }

    @Test
    void categoriasDoVeiculoEDoInstrutorPrecisamBater() {
        veiculo.setCategoria(Categoria.A);
        assertThatThrownBy(() -> service.agendar(req(AULA))).isInstanceOf(RegraNegocioException.class).hasMessageContaining("incompatível");

        // processo AB aceita veículo A, mas o instrutor precisa ensinar A
        processo.setCategoria(Categoria.AB);
        assertThatThrownBy(() -> service.agendar(req(AULA))).isInstanceOf(RegraNegocioException.class).hasMessageContaining("não está habilitado");
        instrutor.definirCategorias(Set.of(Categoria.A, Categoria.B));
        assertThat(service.agendar(req(AULA)).id()).isEqualTo(1L);
        assertThat(AulaService.categoriasDeVeiculoAceitas(Categoria.AB)).containsExactlyInAnyOrder(Categoria.A, Categoria.B);
        assertThat(AulaService.categoriasDeVeiculoAceitas(Categoria.C)).containsExactly(Categoria.C);
    }

    @Test
    void instrutorOuVeiculoInativoECnhVencida() {
        instrutor.setAtivo(false);
        assertThatThrownBy(() -> service.agendar(req(AULA))).isInstanceOf(RegraNegocioException.class);
        instrutor.setAtivo(true);
        veiculo.setAtivo(false);
        assertThatThrownBy(() -> service.agendar(req(AULA))).isInstanceOf(RegraNegocioException.class);
        veiculo.setAtivo(true);
        instrutor.setCnhValidade(LocalDate.of(2026, 11, 2));
        assertThatThrownBy(() -> service.agendar(req(AULA))).isInstanceOf(RegraNegocioException.class).hasMessageContaining("CNH");
    }

    @Test
    @SuppressWarnings("unchecked")
    void faltaSoConsomeCreditoQuandoNaoHaCobranca() {
        var captor = ArgumentCaptor.forClass(java.util.Collection.class);
        service.saldo(60L);
        verify(repo).contarDaMatricula(eq(60L), captor.capture());
        assertThat(captor.getValue()).contains(StatusAula.FALTA);

        cfg.setCobrarFalta(true);
        var captor2 = ArgumentCaptor.forClass(java.util.Collection.class);
        service.saldo(60L);
        verify(repo, org.mockito.Mockito.times(2)).contarDaMatricula(eq(60L), captor2.capture());
        assertThat(captor2.getValue()).doesNotContain(StatusAula.FALTA);
    }

    // ---------- cancelamento ----------

    @Test
    void cancelamentoTardioGeraCobrancaSeConfigurado() {
        cfg.setCobrarFalta(true);
        cfg.setValorFalta(new BigDecimal("40.00"));
        Aula a = aula(Instant.parse("2026-11-01T17:00:00Z")); // 5 h à frente, antecedência = 24 h

        var r = service.cancelar(1L, new CancelarAulaRequest("Doente", null));

        assertThat(r.status()).isEqualTo(StatusAula.CANCELADA);
        assertThat(a.isCancelamentoTardio()).isTrue();
        var t = ArgumentCaptor.forClass(TituloReceber.class);
        verify(titulos).save(t.capture());
        assertThat(t.getValue().getOrigem()).isEqualTo(OrigemTitulo.FALTA);
        assertThat(t.getValue().getValor()).isEqualByComparingTo("40.00");
    }

    @Test
    void equipePodeIsentarEAntecipadoNaoCobra() {
        cfg.setCobrarFalta(true);
        cfg.setValorFalta(new BigDecimal("40.00"));
        aula(Instant.parse("2026-11-01T17:00:00Z"));
        service.cancelar(1L, new CancelarAulaRequest("Chuva", true));
        verify(titulos, never()).save(any());

        Aula b = aula(Instant.parse("2026-11-05T17:00:00Z"));
        service.cancelar(1L, new CancelarAulaRequest("Remarcar", null));
        assertThat(b.isCancelamentoTardio()).isFalse();
        verify(titulos, never()).save(any());
    }

    @Test
    void alunoSoCancelaAsPropriasAulas() {
        aula(Instant.parse("2026-11-05T17:00:00Z"));
        TestSupport.logarComo(99L, 5L, Perfil.ALUNO);
        when(alunosRepo.findByUsuarioId(99L)).thenReturn(Optional.of(aluno));
        assertThat(service.cancelar(1L, new CancelarAulaRequest("Imprevisto", null)).status()).isEqualTo(StatusAula.CANCELADA);

        aula(Instant.parse("2026-11-06T17:00:00Z"));
        Aluno outro = new Aluno();
        outro.setId(31L);
        when(alunosRepo.findByUsuarioId(100L)).thenReturn(Optional.of(outro));
        TestSupport.logarComo(100L, 5L, Perfil.ALUNO);
        assertThatThrownBy(() -> service.cancelar(1L, new CancelarAulaRequest("x", null))).isInstanceOf(NaoEncontradoException.class);
    }

    // ---------- registro ----------

    private RegistroAulaRequest registro(StatusAula r, Integer kmIni, Integer kmFim) {
        return new RegistroAulaRequest(r, kmIni, kmFim, "Baliza", null, 4);
    }

    @Test
    void naoRegistraAulaQueAindaNaoComecou() {
        aula(Instant.parse("2026-11-05T17:00:00Z"));
        assertThatThrownBy(() -> service.registrar(1L, registro(StatusAula.REALIZADA, 100, 110)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("não começou");
    }

    @Test
    void aulaRealizadaMovePrimeiraVezOProcessoParaEmAulas() {
        aula(Instant.parse("2026-11-01T10:00:00Z"));
        var r = service.registrar(1L, registro(StatusAula.REALIZADA, 100, 112));
        assertThat(r.status()).isEqualTo(StatusAula.REALIZADA);
        assertThat(r.kmFinal()).isEqualTo(112);
        assertThat(processo.getSituacao()).isEqualTo(SituacaoProcesso.EM_AULAS);
        assertThatThrownBy(() -> service.registrar(1L, registro(StatusAula.FALTA, null, null)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void kmFinalNaoPodeSerMenorQueOInicial() {
        aula(Instant.parse("2026-11-01T10:00:00Z"));
        assertThatThrownBy(() -> service.registrar(1L, registro(StatusAula.REALIZADA, 200, 100)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("km");
    }

    @Test
    void faltaCobraQuandoConfigurado() {
        cfg.setCobrarFalta(true);
        cfg.setValorFalta(new BigDecimal("50.00"));
        aula(Instant.parse("2026-11-01T10:00:00Z"));
        service.registrar(1L, registro(StatusAula.FALTA, null, null));
        verify(titulos).save(any(TituloReceber.class));
        assertThat(processo.getSituacao()).isEqualTo(SituacaoProcesso.MATRICULADO);
    }

    @Test
    void instrutorSoRegistraAsProprias() {
        aula(Instant.parse("2026-11-01T10:00:00Z"));
        TestSupport.logarComo(88L, 5L, Perfil.INSTRUTOR);
        when(instrutores.findByUsuarioId(88L)).thenReturn(Optional.of(instrutor));
        assertThat(service.registrar(1L, registro(StatusAula.REALIZADA, null, null)).status()).isEqualTo(StatusAula.REALIZADA);

        aula(Instant.parse("2026-11-01T10:00:00Z"));
        Instrutor outro = new Instrutor();
        outro.setId(71L);
        when(instrutores.findByUsuarioId(89L)).thenReturn(Optional.of(outro));
        TestSupport.logarComo(89L, 5L, Perfil.INSTRUTOR);
        assertThatThrownBy(() -> service.registrar(1L, registro(StatusAula.REALIZADA, null, null)))
                .isInstanceOf(NaoEncontradoException.class);
    }
}
