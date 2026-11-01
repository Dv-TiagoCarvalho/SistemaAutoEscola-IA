package br.com.cfc.financeiro;

import br.com.cfc.TestSupport;
import br.com.cfc.aluno.Aluno;
import br.com.cfc.aluno.AlunoRepository;
import br.com.cfc.aluno.AlunoService;
import br.com.cfc.autoescola.AutoescolaRepository;
import br.com.cfc.autoescola.ConfiguracaoAutoescola;
import br.com.cfc.autoescola.ConfiguracaoRepository;
import br.com.cfc.autoescola.ConfiguracaoService;
import br.com.cfc.financeiro.FinanceiroDtos.BaixaRequest;
import br.com.cfc.financeiro.FinanceiroDtos.DespesaRequest;
import br.com.cfc.financeiro.FinanceiroDtos.TituloAvulsoRequest;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.exception.NaoEncontradoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.Perfil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceiroServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 1);

    private final TituloReceberRepository titulos = mock(TituloReceberRepository.class);
    private final PagamentoRepository pagamentos = mock(PagamentoRepository.class);
    private final DespesaRepository despesas = mock(DespesaRepository.class);
    private final ConfiguracaoRepository configRepo = mock(ConfiguracaoRepository.class);
    private final ConfiguracaoService config = mock(ConfiguracaoService.class);
    private final AlunoService alunos = mock(AlunoService.class);
    private FinanceiroService service;
    private ConfiguracaoAutoescola cfg;
    private TituloReceber titulo;

    @BeforeEach
    void setUp() {
        service = new FinanceiroService(titulos, pagamentos, despesas, configRepo, config, alunos,
                mock(AlunoRepository.class), mock(AutoescolaRepository.class), new ManualPaymentProvider(),
                new ReciboPdfService(), mock(AuditService.class), new TestSupport.RelogioFalso());
        TestSupport.logarComo(1L, 5L, Perfil.SECRETARIA);
        when(config.hoje()).thenReturn(HOJE);
        cfg = new ConfiguracaoAutoescola();
        cfg.setAutoescolaId(5L);
        when(configRepo.buscarComLock(5L)).thenReturn(Optional.of(cfg));
        Aluno a = new Aluno();
        a.setId(30L);
        a.setAutoescolaId(5L);
        a.setNome("Maria");
        when(alunos.exigir(30L)).thenReturn(a);
        when(alunos.nomes(anyCollection())).thenReturn(Map.of(30L, "Maria"));

        titulo = new TituloReceber();
        titulo.setId(100L);
        titulo.setAutoescolaId(5L);
        titulo.setAlunoId(30L);
        titulo.setDescricao("Parcela 1/3");
        titulo.setValor(new BigDecimal("300.00"));
        titulo.setVencimento(HOJE.minusDays(10));
        titulo.setStatus(StatusTitulo.ABERTO);
        when(titulos.buscarComLock(100L)).thenReturn(Optional.of(titulo));
        when(titulos.findById(100L)).thenReturn(Optional.of(titulo));
        when(pagamentos.save(any())).thenAnswer(i -> {
            Pagamento p = i.getArgument(0);
            p.setId(7L);
            p.setAutoescolaId(5L);
            when(pagamentos.findById(7L)).thenReturn(Optional.of(p));
            return p;
        });
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void baixaCalculaValorPagoNumeraReciboEFechaOTitulo() {
        var r = service.baixar(100L, new BaixaRequest(FormaPagamento.PIX, null, new BigDecimal("6.00"), new BigDecimal("1.50")));
        assertThat(r.valor()).isEqualByComparingTo("304.50");
        assertThat(r.reciboNumero()).isEqualTo("000001");
        assertThat(r.dataPagamento()).isEqualTo(HOJE);
        assertThat(titulo.getStatus()).isEqualTo(StatusTitulo.PAGO);
        assertThat(cfg.getProximoRecibo()).isEqualTo(2);
    }

    @Test
    void naoPagaDuasVezesNemNoFuturoNemComDescontoAbusivo() {
        assertThatThrownBy(() -> service.baixar(100L, new BaixaRequest(FormaPagamento.DINHEIRO, HOJE.plusDays(1), null, null)))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.baixar(100L, new BaixaRequest(FormaPagamento.DINHEIRO, null, null, new BigDecimal("300.00"))))
                .isInstanceOf(RegraNegocioException.class);
        service.baixar(100L, new BaixaRequest(FormaPagamento.DINHEIRO, null, null, null));
        assertThatThrownBy(() -> service.baixar(100L, new BaixaRequest(FormaPagamento.DINHEIRO, null, null, null)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("em aberto");
    }

    @Test
    void estornoReabreOTituloEUmSoEstorno() {
        var pago = service.baixar(100L, new BaixaRequest(FormaPagamento.PIX, null, null, null));
        var r = service.estornar(pago.id(), "Pagamento duplicado");
        assertThat(r.estornado()).isTrue();
        assertThat(titulo.getStatus()).isEqualTo(StatusTitulo.ABERTO);
        assertThatThrownBy(() -> service.estornar(pago.id(), "de novo")).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void cancelarSoTituloEmAberto() {
        var r = service.cancelarTitulo(100L, "Aluno desistiu");
        assertThat(r.status()).isEqualTo(StatusTitulo.CANCELADO);
        assertThatThrownBy(() -> service.cancelarTitulo(100L, "x")).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void tituloDeOutraAutoescolaNaoEncontrado() {
        titulo.setAutoescolaId(99L);
        assertThatThrownBy(() -> service.baixar(100L, new BaixaRequest(FormaPagamento.PIX, null, null, null)))
                .isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void tituloAvulsoNaoAceitaVencimentoPassado() {
        assertThatThrownBy(() -> service.criarAvulso(new TituloAvulsoRequest(30L, "Taxa", new BigDecimal("50.00"), HOJE.minusDays(1))))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void despesaPagaNaoPodeSerPagaDeNovoNemNoFuturo() {
        when(despesas.save(any())).thenAnswer(i -> {
            Despesa d = i.getArgument(0);
            d.setId(9L);
            when(despesas.findById(9L)).thenReturn(Optional.of(d));
            d.setAutoescolaId(5L);
            return d;
        });
        var d = service.criarDespesa(new DespesaRequest("Aluguel", CategoriaDespesa.ALUGUEL, new BigDecimal("2000.00"), HOJE));
        assertThatThrownBy(() -> service.pagarDespesa(d.id(), HOJE.plusDays(2))).isInstanceOf(RegraNegocioException.class);
        assertThat(service.pagarDespesa(d.id(), null).status()).isEqualTo(StatusDespesa.PAGA);
        assertThatThrownBy(() -> service.pagarDespesa(d.id(), null)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void reciboGeraPdf() {
        var pago = service.baixar(100L, new BaixaRequest(FormaPagamento.PIX, null, null, null));
        var ae = new br.com.cfc.autoescola.Autoescola();
        ae.setNome("CFC Exemplo");
        var repo = mock(AutoescolaRepository.class);
        when(repo.findById(5L)).thenReturn(Optional.of(ae));
        var s = new FinanceiroService(titulos, pagamentos, despesas, configRepo, config, alunos, mock(AlunoRepository.class),
                repo, new ManualPaymentProvider(), new ReciboPdfService(), mock(AuditService.class), new TestSupport.RelogioFalso());
        byte[] pdf = s.recibo(pago.id());
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }
}
