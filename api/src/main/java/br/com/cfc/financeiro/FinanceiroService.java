package br.com.cfc.financeiro;

import br.com.cfc.aluno.Aluno;
import br.com.cfc.aluno.AlunoRepository;
import br.com.cfc.aluno.AlunoService;
import br.com.cfc.autoescola.Autoescola;
import br.com.cfc.autoescola.AutoescolaRepository;
import br.com.cfc.autoescola.ConfiguracaoRepository;
import br.com.cfc.autoescola.ConfiguracaoAutoescola;
import br.com.cfc.autoescola.ConfiguracaoService;
import br.com.cfc.financeiro.FinanceiroDtos.*;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.domain.Tenants;
import br.com.cfc.shared.exception.NaoEncontradoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.SecurityUtils;
import br.com.cfc.shared.security.UsuarioAutenticado;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.shared.validation.Documentos;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Contas a receber (baixa, estorno, recibo), despesas e resumo financeiro. */
@Service
public class FinanceiroService {

    private final TituloReceberRepository titulos;
    private final PagamentoRepository pagamentos;
    private final DespesaRepository despesas;
    private final ConfiguracaoRepository configuracaoRepository;
    private final ConfiguracaoService configuracaoService;
    private final AlunoService alunoService;
    private final AlunoRepository alunoRepository;
    private final AutoescolaRepository autoescolaRepository;
    private final PaymentProvider provider;
    private final ReciboPdfService reciboPdf;
    private final AuditService auditService;
    private final Clock clock;

    public FinanceiroService(TituloReceberRepository titulos, PagamentoRepository pagamentos, DespesaRepository despesas,
                             ConfiguracaoRepository configuracaoRepository, ConfiguracaoService configuracaoService,
                             AlunoService alunoService, AlunoRepository alunoRepository,
                             AutoescolaRepository autoescolaRepository, PaymentProvider provider,
                             ReciboPdfService reciboPdf, AuditService auditService, Clock clock) {
        this.titulos = titulos;
        this.pagamentos = pagamentos;
        this.despesas = despesas;
        this.configuracaoRepository = configuracaoRepository;
        this.configuracaoService = configuracaoService;
        this.alunoService = alunoService;
        this.alunoRepository = alunoRepository;
        this.autoescolaRepository = autoescolaRepository;
        this.provider = provider;
        this.reciboPdf = reciboPdf;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ---------- Títulos ----------

    @Transactional(readOnly = true)
    public List<TituloResponse> listar(Long alunoId, StatusTitulo status, LocalDate de, LocalDate ate, boolean apenasVencidos) {
        SecurityUtils.autoescolaIdObrigatorio();
        LocalDate hoje = configuracaoService.hoje();
        if (apenasVencidos) {
            status = StatusTitulo.ABERTO;
            ate = ate == null || ate.isAfter(hoje.minusDays(1)) ? hoje.minusDays(1) : ate;
        }
        return montar(titulos.buscar(alunoId, status, de, ate, PageRequest.of(0, 500)), hoje);
    }

    /** Títulos do aluno logado (perfil ALUNO). */
    @Transactional(readOnly = true)
    public List<TituloResponse> meus() {
        UsuarioAutenticado u = SecurityUtils.usuarioObrigatorio();
        Aluno aluno = alunoRepository.findByUsuarioId(u.id()).orElse(null);
        if (aluno == null) {
            return List.of();
        }
        return montar(titulos.findByAlunoIdOrderByVencimentoAscIdAsc(aluno.getId()), configuracaoService.hoje());
    }

    private List<TituloResponse> montar(List<TituloReceber> lista, LocalDate hoje) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nomes = alunoService.nomes(lista.stream().map(TituloReceber::getAlunoId).distinct().toList());
        Map<Long, Pagamento> pagos = pagamentos.findByTituloIdInAndEstornadoFalse(lista.stream().map(TituloReceber::getId).toList())
                .stream().collect(Collectors.toMap(Pagamento::getTituloId, Function.identity(), (a, b) -> a));
        return lista.stream().map(t -> {
            Pagamento p = pagos.get(t.getId());
            return new TituloResponse(t.getId(), t.getAlunoId(), nomes.get(t.getAlunoId()), t.getMatriculaId(),
                    t.getOrigem(), t.getDescricao(), t.getNumeroParcela(), t.getTotalParcelas(), t.getValor(),
                    t.getVencimento(), t.getStatus(), t.getStatus() == StatusTitulo.ABERTO && t.getVencimento().isBefore(hoje),
                    p == null ? null : p.getId(), p == null ? null : p.getReciboNumero(), p == null ? null : p.getDataPagamento());
        }).toList();
    }

    @Transactional
    public TituloResponse criarAvulso(TituloAvulsoRequest req) {
        SecurityUtils.autoescolaIdObrigatorio();
        Aluno aluno = alunoService.exigir(req.alunoId());
        LocalDate hoje = configuracaoService.hoje();
        if (req.vencimento().isBefore(hoje)) {
            throw new RegraNegocioException("O vencimento não pode ser no passado.");
        }
        TituloReceber t = new TituloReceber();
        t.setAlunoId(aluno.getId());
        t.setOrigem(OrigemTitulo.OUTROS);
        t.setDescricao(req.descricao().trim());
        t.setNumeroParcela(1);
        t.setTotalParcelas(1);
        t.setValor(req.valor());
        t.setVencimento(req.vencimento());
        t.setStatus(StatusTitulo.ABERTO);
        t = titulos.save(t);
        auditService.registrar("titulo_receber", t.getId(), AcaoAuditoria.CRIACAO, null, req);
        return montar(List.of(t), hoje).get(0);
    }

    @Transactional
    public TituloResponse cancelarTitulo(Long id, String motivo) {
        TituloReceber t = Tenants.daAutoescolaAtual(titulos.buscarComLock(id), "Título não encontrado.");
        if (t.getStatus() != StatusTitulo.ABERTO) {
            throw new RegraNegocioException("Só é possível cancelar títulos em aberto. Para título pago, estorne o pagamento.");
        }
        t.setStatus(StatusTitulo.CANCELADO);
        auditService.registrar("titulo_receber", id, AcaoAuditoria.ALTERACAO, null, Map.of("status", "CANCELADO", "motivo", motivo));
        return montar(List.of(t), configuracaoService.hoje()).get(0);
    }

    // ---------- Baixa / estorno / recibo ----------

    @Transactional
    public PagamentoResponse baixar(Long tituloId, BaixaRequest req) {
        Long autoescolaId = SecurityUtils.autoescolaIdObrigatorio();
        TituloReceber t = Tenants.daAutoescolaAtual(titulos.buscarComLock(tituloId), "Título não encontrado.");
        if (t.getStatus() != StatusTitulo.ABERTO) {
            throw new RegraNegocioException("Este título não está em aberto (" + t.getStatus() + ").");
        }
        LocalDate hoje = configuracaoService.hoje();
        LocalDate data = req.dataPagamento() == null ? hoje : req.dataPagamento();
        if (data.isAfter(hoje)) {
            throw new RegraNegocioException("A data do pagamento não pode ser no futuro.");
        }
        BigDecimal acrescimo = req.acrescimo() == null ? BigDecimal.ZERO : req.acrescimo();
        BigDecimal desconto = req.desconto() == null ? BigDecimal.ZERO : req.desconto();
        if (desconto.compareTo(t.getValor().add(acrescimo)) >= 0) {
            throw new RegraNegocioException("O desconto deve ser menor que o valor a pagar.");
        }
        BigDecimal pago = t.getValor().add(acrescimo).subtract(desconto);

        var resultado = provider.registrar(new PaymentProvider.SolicitacaoPagamento(t.getId(), pago, req.forma()));
        if (!resultado.confirmado()) {
            throw new RegraNegocioException("O pagamento não foi confirmado: " + resultado.mensagem());
        }

        Pagamento p = new Pagamento();
        p.setTituloId(t.getId());
        p.setAlunoId(t.getAlunoId());
        p.setValor(pago);
        p.setAcrescimo(acrescimo);
        p.setDesconto(desconto);
        p.setForma(req.forma());
        p.setDataPagamento(data);
        p.setReciboNumero(proximoRecibo(autoescolaId));
        p.setProvider(provider.nome());
        p.setReferenciaExterna(resultado.referenciaExterna());
        p = pagamentos.save(p);
        t.setStatus(StatusTitulo.PAGO);
        var resposta = PagamentoResponse.de(p);
        auditService.registrar("pagamento", p.getId(), AcaoAuditoria.CRIACAO, null, resposta);
        return resposta;
    }

    private String proximoRecibo(Long autoescolaId) {
        ConfiguracaoAutoescola c = configuracaoRepository.buscarComLock(autoescolaId)
                .orElseThrow(() -> new NaoEncontradoException("Configurações da autoescola não encontradas."));
        long n = c.getProximoRecibo();
        c.setProximoRecibo(n + 1);
        return String.format("%06d", n);
    }

    @Transactional
    public PagamentoResponse estornar(Long pagamentoId, String motivo) {
        Pagamento p = exigirPagamento(pagamentoId);
        if (p.isEstornado()) {
            throw new RegraNegocioException("Este pagamento já foi estornado.");
        }
        TituloReceber t = Tenants.daAutoescolaAtual(titulos.buscarComLock(p.getTituloId()), "Título não encontrado.");
        var antes = PagamentoResponse.de(p);
        p.setEstornado(true);
        p.setEstornadoEm(clock.instant());
        p.setMotivoEstorno(motivo.trim());
        t.setStatus(StatusTitulo.ABERTO);
        var depois = PagamentoResponse.de(p);
        auditService.registrar("pagamento", p.getId(), AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    @Transactional(readOnly = true)
    public byte[] recibo(Long pagamentoId) {
        Long autoescolaId = SecurityUtils.autoescolaIdObrigatorio();
        UsuarioAutenticado u = SecurityUtils.usuarioObrigatorio();
        Pagamento p = exigirPagamento(pagamentoId);
        if (u.perfil() == Perfil.ALUNO) {
            Aluno proprio = alunoRepository.findByUsuarioId(u.id()).orElse(null);
            if (proprio == null || !proprio.getId().equals(p.getAlunoId())) {
                throw new NaoEncontradoException("Pagamento não encontrado.");
            }
        }
        TituloReceber t = Tenants.daAutoescolaAtual(titulos.findById(p.getTituloId()), "Título não encontrado.");
        Aluno aluno = alunoService.exigir(p.getAlunoId());
        Autoescola ae = autoescolaRepository.findById(autoescolaId).orElseThrow(() -> new NaoEncontradoException("Autoescola não encontrada."));
        return reciboPdf.gerar(new ReciboPdfService.Dados(ae.getNome(), Documentos.formatarCnpj(ae.getCnpj()), p.getReciboNumero(),
                aluno.getNome(), Documentos.formatarCpf(aluno.getCpf()), t.getDescricao(), p.getForma(), p.getDataPagamento(),
                t.getValor(), p.getAcrescimo(), p.getDesconto(), p.getValor(), p.isEstornado()));
    }

    private Pagamento exigirPagamento(Long id) {
        return Tenants.daAutoescolaAtual(pagamentos.findById(id), "Pagamento não encontrado.");
    }

    // ---------- Despesas ----------

    @Transactional(readOnly = true)
    public List<DespesaResponse> listarDespesas(YearMonth mes, StatusDespesa status) {
        SecurityUtils.autoescolaIdObrigatorio();
        YearMonth m = mes == null ? YearMonth.from(configuracaoService.hoje()) : mes;
        LocalDate hoje = configuracaoService.hoje();
        return despesas.buscar(m.atDay(1), m.atEndOfMonth(), status).stream().map(d -> DespesaResponse.de(d, hoje)).toList();
    }

    @Transactional
    public DespesaResponse criarDespesa(DespesaRequest req) {
        SecurityUtils.autoescolaIdObrigatorio();
        Despesa d = new Despesa();
        d.setDescricao(req.descricao().trim());
        d.setCategoria(req.categoria());
        d.setValor(req.valor());
        d.setVencimento(req.vencimento());
        d.setStatus(StatusDespesa.ABERTA);
        d = despesas.save(d);
        var r = DespesaResponse.de(d, configuracaoService.hoje());
        auditService.registrar("despesa", d.getId(), AcaoAuditoria.CRIACAO, null, r);
        return r;
    }

    @Transactional
    public DespesaResponse pagarDespesa(Long id, LocalDate dataPagamento) {
        Despesa d = exigirDespesa(id);
        if (d.getStatus() != StatusDespesa.ABERTA) {
            throw new RegraNegocioException("A despesa não está em aberto.");
        }
        LocalDate hoje = configuracaoService.hoje();
        LocalDate data = dataPagamento == null ? hoje : dataPagamento;
        if (data.isAfter(hoje)) {
            throw new RegraNegocioException("A data do pagamento não pode ser no futuro.");
        }
        d.setStatus(StatusDespesa.PAGA);
        d.setDataPagamento(data);
        var r = DespesaResponse.de(d, hoje);
        auditService.registrar("despesa", id, AcaoAuditoria.ALTERACAO, null, r);
        return r;
    }

    @Transactional
    public DespesaResponse cancelarDespesa(Long id) {
        Despesa d = exigirDespesa(id);
        if (d.getStatus() != StatusDespesa.ABERTA) {
            throw new RegraNegocioException("Só é possível cancelar despesas em aberto.");
        }
        d.setStatus(StatusDespesa.CANCELADA);
        var r = DespesaResponse.de(d, configuracaoService.hoje());
        auditService.registrar("despesa", id, AcaoAuditoria.ALTERACAO, null, r);
        return r;
    }

    private Despesa exigirDespesa(Long id) {
        return Tenants.daAutoescolaAtual(despesas.findById(id), "Despesa não encontrada.");
    }

    // ---------- Resumo ----------

    @Transactional(readOnly = true)
    public ResumoFinanceiro resumo(YearMonth mes) {
        SecurityUtils.autoescolaIdObrigatorio();
        LocalDate hoje = configuracaoService.hoje();
        YearMonth m = mes == null ? YearMonth.from(hoje) : mes;
        BigDecimal recebido = pagamentos.somarNoPeriodo(m.atDay(1), m.atEndOfMonth());
        BigDecimal pagas = despesas.somarPagasNoPeriodo(m.atDay(1), m.atEndOfMonth());
        List<MesFluxo> serie = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth x = m.minusMonths(i);
            serie.add(new MesFluxo(x.toString(), pagamentos.somarNoPeriodo(x.atDay(1), x.atEndOfMonth()),
                    despesas.somarPagasNoPeriodo(x.atDay(1), x.atEndOfMonth())));
        }
        return new ResumoFinanceiro(m.toString(), titulos.somarAbertosAte(null), titulos.somarAbertosAte(hoje.minusDays(1)),
                titulos.contarVencidos(hoje), recebido, pagas, despesas.somarAbertas(), recebido.subtract(pagas), serie);
    }
}
