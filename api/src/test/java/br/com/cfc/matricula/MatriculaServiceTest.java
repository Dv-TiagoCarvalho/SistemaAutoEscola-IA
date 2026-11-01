package br.com.cfc.matricula;

import br.com.cfc.TestSupport;
import br.com.cfc.aluno.Aluno;
import br.com.cfc.aluno.AlunoService;
import br.com.cfc.aluno.ProcessoHabilitacao;
import br.com.cfc.aluno.ProcessoService;
import br.com.cfc.aluno.SituacaoProcesso;
import br.com.cfc.autoescola.Autoescola;
import br.com.cfc.autoescola.AutoescolaRepository;
import br.com.cfc.autoescola.Categoria;
import br.com.cfc.autoescola.ConfiguracaoAutoescola;
import br.com.cfc.autoescola.ConfiguracaoService;
import br.com.cfc.financeiro.StatusTitulo;
import br.com.cfc.financeiro.TituloReceber;
import br.com.cfc.financeiro.TituloReceberRepository;
import br.com.cfc.matricula.MatriculaDtos.AulaAvulsaRequest;
import br.com.cfc.matricula.MatriculaDtos.MatriculaRequest;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.exception.NaoEncontradoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.shared.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatriculaServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 1);

    private final MatriculaRepository repo = mock(MatriculaRepository.class);
    private final CreditoAulaRepository creditos = mock(CreditoAulaRepository.class);
    private final TituloReceberRepository titulos = mock(TituloReceberRepository.class);
    private final ProcessoService processos = mock(ProcessoService.class);
    private final AlunoService alunos = mock(AlunoService.class);
    private final PacoteService pacotes = mock(PacoteService.class);
    private final ConfiguracaoService config = mock(ConfiguracaoService.class);
    private final AutoescolaRepository autoescolas = mock(AutoescolaRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private MatriculaService service;
    private ProcessoHabilitacao processo;
    private Pacote pacote;

    @BeforeEach
    void setUp() {
        service = new MatriculaService(repo, creditos, titulos, processos, alunos, pacotes, config, autoescolas,
                new ContratoPdfService(), storage, mock(AuditService.class), new TestSupport.RelogioFalso());
        TestSupport.logarComo(1L, 5L, Perfil.SECRETARIA);

        when(config.hoje()).thenReturn(HOJE);
        ConfiguracaoAutoescola cfg = new ConfiguracaoAutoescola();
        cfg.setAutoescolaId(5L);
        when(config.configuracaoAtual()).thenReturn(cfg);

        Autoescola ae = new Autoescola();
        ae.setId(5L);
        ae.setNome("CFC Exemplo");
        ae.setCnpj("11222333000181");
        ae.setCidade("Belo Horizonte");
        ae.setUf("MG");
        when(autoescolas.findById(5L)).thenReturn(Optional.of(ae));

        Aluno aluno = new Aluno();
        aluno.setId(30L);
        aluno.setAutoescolaId(5L);
        aluno.setNome("Maria Silva");
        aluno.setCpf("52998224725");
        aluno.setTelefone("31999990000");
        aluno.setDataNascimento(LocalDate.of(2000, 1, 1));
        aluno.setAtivo(true);
        when(alunos.exigir(30L)).thenReturn(aluno);
        when(alunos.nomes(anyCollection())).thenReturn(Map.of(30L, "Maria Silva"));

        processo = new ProcessoHabilitacao();
        processo.setId(40L);
        processo.setAutoescolaId(5L);
        processo.setAlunoId(30L);
        processo.setCategoria(Categoria.B);
        processo.setSituacao(SituacaoProcesso.MATRICULADO);
        when(processos.exigirComLock(40L)).thenReturn(processo);

        pacote = new Pacote();
        pacote.setId(50L);
        pacote.setAutoescolaId(5L);
        pacote.setCategoria(Categoria.B);
        pacote.setNome("Pacote B");
        pacote.setQtdAulasPraticas(20);
        pacote.setTeoricoIncluso(true);
        pacote.setPreco(new BigDecimal("1200.00"));
        pacote.setAtivo(true);
        when(pacotes.exigir(50L)).thenReturn(pacote);

        when(repo.save(any())).thenAnswer(i -> {
            Matricula m = i.getArgument(0);
            m.setId(60L);
            return m;
        });
        when(repo.findById(60L)).thenAnswer(i -> Optional.of(matriculaAtiva()));
        when(creditos.findByMatriculaIdOrderByIdAsc(any())).thenReturn(List.of());
        when(titulos.findByMatriculaIdOrderByVencimentoAscIdAsc(any())).thenReturn(List.of());
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private Matricula matriculaAtiva() {
        Matricula m = new Matricula();
        m.setId(60L);
        m.setAutoescolaId(5L);
        m.setAlunoId(30L);
        m.setProcessoId(40L);
        m.setPacoteId(50L);
        m.setCategoria(Categoria.B);
        m.setPacoteNome("Pacote B");
        m.setDataMatricula(HOJE);
        m.setValorBruto(new BigDecimal("1200.00"));
        m.setValorTotal(new BigDecimal("1200.00"));
        m.setStatus(StatusMatricula.ATIVA);
        return m;
    }

    private MatriculaRequest req(String desconto, String entrada, int parcelas) {
        return new MatriculaRequest(40L, 50L, desconto == null ? null : new BigDecimal(desconto),
                entrada == null ? null : new BigDecimal(entrada), parcelas, HOJE.plusDays(30));
    }

    @Test
    @SuppressWarnings("unchecked")
    void criaMatriculaComCreditosParcelasEContrato() {
        service.criar(req("100.00", "200.00", 3));

        var m = ArgumentCaptor.forClass(Matricula.class);
        verify(repo).save(m.capture());
        assertThat(m.getValue().getValorTotal()).isEqualByComparingTo("1100.00");
        assertThat(m.getValue().getPacoteNome()).isEqualTo("Pacote B");
        assertThat(m.getValue().getAulasPraticas()).isEqualTo(20);
        assertThat(m.getValue().getContratoPath()).isEqualTo("5/contratos/matricula-60.pdf");

        var credito = ArgumentCaptor.forClass(CreditoAula.class);
        verify(creditos).save(credito.capture());
        assertThat(credito.getValue().getOrigem()).isEqualTo(OrigemCredito.PACOTE);
        assertThat(credito.getValue().getQuantidade()).isEqualTo(20);

        var lista = ArgumentCaptor.forClass(List.class);
        verify(titulos).saveAll(lista.capture());
        List<TituloReceber> gerados = lista.getValue();
        assertThat(gerados).hasSize(4); // entrada + 3
        assertThat(gerados.stream().map(TituloReceber::getValor).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("1100.00");
        assertThat(gerados).allMatch(t -> t.getStatus() == StatusTitulo.ABERTO && t.getTotalParcelas() == 4);

        verify(storage).salvar(anyString(), any());
    }

    @Test
    void cortesiaTotalNaoGeraParcelas() {
        service.criar(req("1200.00", null, 1));
        verify(titulos).saveAll(List.of());
    }

    @Test
    void pacoteDeOutraCategoria() {
        pacote.setCategoria(Categoria.A);
        assertThatThrownBy(() -> service.criar(req(null, null, 1))).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void pacoteInativoOuProcessoEncerrado() {
        pacote.setAtivo(false);
        assertThatThrownBy(() -> service.criar(req(null, null, 1))).isInstanceOf(RegraNegocioException.class);
        pacote.setAtivo(true);
        processo.setSituacao(SituacaoProcesso.DESISTENTE);
        assertThatThrownBy(() -> service.criar(req(null, null, 1))).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void jaTemMatriculaAtivaNoProcesso() {
        when(repo.existsByProcessoIdAndStatus(40L, StatusMatricula.ATIVA)).thenReturn(true);
        assertThatThrownBy(() -> service.criar(req(null, null, 1))).isInstanceOf(ConflitoException.class);
    }

    @Test
    void descontoMaiorQueOPacote() {
        assertThatThrownBy(() -> service.criar(req("1200.01", null, 1))).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void vencimentoNoPassado() {
        var r = new MatriculaRequest(40L, 50L, null, null, 2, HOJE.minusDays(1));
        assertThatThrownBy(() -> service.criar(r)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void cancelarFechaMatriculaECancelaSoParcelasEmAberto() {
        TituloReceber aberto = new TituloReceber();
        aberto.setStatus(StatusTitulo.ABERTO);
        aberto.setVencimento(HOJE);
        when(titulos.findByMatriculaIdAndStatus(60L, StatusTitulo.ABERTO)).thenReturn(new ArrayList<>(List.of(aberto)));
        Matricula m = matriculaAtiva();
        when(repo.findById(60L)).thenReturn(Optional.of(m));

        service.cancelar(60L, "  Desistiu  ");

        assertThat(m.getStatus()).isEqualTo(StatusMatricula.CANCELADA);
        assertThat(m.getMotivoCancelamento()).isEqualTo("Desistiu");
        assertThat(aberto.getStatus()).isEqualTo(StatusTitulo.CANCELADO);
        assertThatThrownBy(() -> service.cancelar(60L, "de novo")).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void aulaAvulsaGeraCreditoETitulo() {
        service.adicionarAulasAvulsas(60L, new AulaAvulsaRequest(3, new BigDecimal("85.50"), HOJE.plusDays(5)));

        var t = ArgumentCaptor.forClass(TituloReceber.class);
        verify(titulos).save(t.capture());
        assertThat(t.getValue().getValor()).isEqualByComparingTo("256.50");
        var c = ArgumentCaptor.forClass(CreditoAula.class);
        verify(creditos).save(c.capture());
        assertThat(c.getValue().getOrigem()).isEqualTo(OrigemCredito.AVULSA);
        assertThat(c.getValue().getQuantidade()).isEqualTo(3);
    }

    @Test
    void matriculaDeOutraAutoescolaNaoEncontrada() {
        Matricula outra = matriculaAtiva();
        outra.setAutoescolaId(99L);
        when(repo.findById(60L)).thenReturn(Optional.of(outra));
        assertThatThrownBy(() -> service.detalhar(60L)).isInstanceOf(NaoEncontradoException.class);
    }
}
