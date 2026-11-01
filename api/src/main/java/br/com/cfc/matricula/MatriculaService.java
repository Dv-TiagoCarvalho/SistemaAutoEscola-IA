package br.com.cfc.matricula;

import br.com.cfc.aluno.Aluno;
import br.com.cfc.aluno.AlunoService;
import br.com.cfc.aluno.ProcessoHabilitacao;
import br.com.cfc.aluno.ProcessoService;
import br.com.cfc.autoescola.Autoescola;
import br.com.cfc.autoescola.AutoescolaRepository;
import br.com.cfc.autoescola.ConfiguracaoAutoescola;
import br.com.cfc.autoescola.ConfiguracaoService;
import br.com.cfc.financeiro.OrigemTitulo;
import br.com.cfc.financeiro.StatusTitulo;
import br.com.cfc.financeiro.TituloReceber;
import br.com.cfc.financeiro.TituloReceberRepository;
import br.com.cfc.matricula.MatriculaDtos.AulaAvulsaRequest;
import br.com.cfc.matricula.MatriculaDtos.CreditoResponse;
import br.com.cfc.matricula.MatriculaDtos.MatriculaAuditoria;
import br.com.cfc.matricula.MatriculaDtos.MatriculaDetalheResponse;
import br.com.cfc.matricula.MatriculaDtos.MatriculaRequest;
import br.com.cfc.matricula.MatriculaDtos.MatriculaResponse;
import br.com.cfc.matricula.MatriculaDtos.TituloResponse;
import br.com.cfc.matricula.Parcelamento.Parcela;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.domain.Tenants;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.exception.NaoEncontradoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.SecurityUtils;
import br.com.cfc.shared.storage.StorageService;
import br.com.cfc.shared.validation.Documentos;
import br.com.cfc.shared.web.PaginaResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class MatriculaService {

    private final MatriculaRepository repository;
    private final CreditoAulaRepository creditoRepository;
    private final TituloReceberRepository tituloRepository;
    private final ProcessoService processoService;
    private final AlunoService alunoService;
    private final PacoteService pacoteService;
    private final ConfiguracaoService configuracaoService;
    private final AutoescolaRepository autoescolaRepository;
    private final ContratoPdfService contratoPdfService;
    private final StorageService storage;
    private final AuditService auditService;
    private final Clock clock;

    public MatriculaService(MatriculaRepository repository, CreditoAulaRepository creditoRepository,
                            TituloReceberRepository tituloRepository, ProcessoService processoService,
                            AlunoService alunoService, PacoteService pacoteService,
                            ConfiguracaoService configuracaoService, AutoescolaRepository autoescolaRepository,
                            ContratoPdfService contratoPdfService, StorageService storage,
                            AuditService auditService, Clock clock) {
        this.repository = repository;
        this.creditoRepository = creditoRepository;
        this.tituloRepository = tituloRepository;
        this.processoService = processoService;
        this.alunoService = alunoService;
        this.pacoteService = pacoteService;
        this.configuracaoService = configuracaoService;
        this.autoescolaRepository = autoescolaRepository;
        this.contratoPdfService = contratoPdfService;
        this.storage = storage;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<MatriculaResponse> listar(Long alunoId, StatusMatricula status, int pagina, int tamanho) {
        SecurityUtils.autoescolaIdObrigatorio();
        var page = repository.buscar(alunoId, status,
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 100)));
        Map<Long, String> nomes = alunoService.nomes(
                page.getContent().stream().map(Matricula::getAlunoId).distinct().toList());
        return PaginaResponse.de(page.map(m -> MatriculaResponse.de(m, nomes.get(m.getAlunoId()))));
    }

    @Transactional(readOnly = true)
    public MatriculaDetalheResponse detalhar(Long id) {
        return detalhe(exigir(id));
    }

    @Transactional
    public MatriculaDetalheResponse criar(MatriculaRequest req) {
        Long autoescolaId = SecurityUtils.autoescolaIdObrigatorio();

        // Trava o processo: duas matrículas ao mesmo tempo no mesmo processo não passam juntas.
        ProcessoHabilitacao processo = processoService.exigirComLock(req.processoId());
        if (ProcessoService.ENCERRADAS.contains(processo.getSituacao())) {
            throw new RegraNegocioException("O processo está encerrado (" + processo.getSituacao() + ").");
        }
        Aluno aluno = alunoService.exigir(processo.getAlunoId());
        if (!aluno.isAtivo()) {
            throw new RegraNegocioException("Aluno inativo. Reative o cadastro antes de matricular.");
        }
        Pacote pacote = pacoteService.exigir(req.pacoteId());
        if (!pacote.isAtivo()) {
            throw new RegraNegocioException("O pacote está inativo.");
        }
        if (pacote.getCategoria() != processo.getCategoria()) {
            throw new RegraNegocioException("O pacote é da categoria " + pacote.getCategoria()
                    + ", mas o processo do aluno é da categoria " + processo.getCategoria() + ".");
        }
        if (repository.existsByProcessoIdAndStatus(processo.getId(), StatusMatricula.ATIVA)) {
            throw new ConflitoException("Este processo já tem uma matrícula ativa.");
        }

        BigDecimal desconto = req.desconto() == null ? BigDecimal.ZERO : req.desconto();
        if (desconto.compareTo(pacote.getPreco()) > 0) {
            throw new RegraNegocioException("O desconto não pode ser maior que o valor do pacote.");
        }
        BigDecimal total = pacote.getPreco().subtract(desconto);
        LocalDate hoje = configuracaoService.hoje();

        List<Parcela> parcelas = List.of();
        if (total.signum() > 0) {
            if (req.primeiroVencimento() != null && req.primeiroVencimento().isBefore(hoje)) {
                throw new RegraNegocioException("O vencimento da primeira parcela não pode ser no passado.");
            }
            parcelas = Parcelamento.calcular(total, req.entrada(), hoje, req.numParcelas(), req.primeiroVencimento());
        }

        Matricula m = new Matricula();
        m.setProcessoId(processo.getId());
        m.setAlunoId(aluno.getId());
        m.setPacoteId(pacote.getId());
        m.setCategoria(pacote.getCategoria());
        m.setPacoteNome(pacote.getNome());
        m.setAulasPraticas(pacote.getQtdAulasPraticas());
        m.setTeoricoIncluso(pacote.isTeoricoIncluso());
        m.setDataMatricula(hoje);
        m.setValorBruto(pacote.getPreco());
        m.setDesconto(desconto);
        m.setValorTotal(total);
        m.setStatus(StatusMatricula.ATIVA);
        m = repository.save(m);

        if (pacote.getQtdAulasPraticas() > 0) {
            CreditoAula credito = new CreditoAula();
            credito.setMatriculaId(m.getId());
            credito.setAlunoId(aluno.getId());
            credito.setOrigem(OrigemCredito.PACOTE);
            credito.setQuantidade(pacote.getQtdAulasPraticas());
            credito.setDescricao("Pacote " + pacote.getNome());
            creditoRepository.save(credito);
        }

        List<TituloReceber> titulos = new ArrayList<>();
        for (Parcela p : parcelas) {
            TituloReceber t = new TituloReceber();
            t.setAlunoId(aluno.getId());
            t.setMatriculaId(m.getId());
            t.setOrigem(OrigemTitulo.MATRICULA);
            t.setDescricao(p.descricao());
            t.setNumeroParcela(p.numero());
            t.setTotalParcelas(parcelas.size());
            t.setValor(p.valor());
            t.setVencimento(p.vencimento());
            t.setStatus(StatusTitulo.ABERTO);
            titulos.add(t);
        }
        tituloRepository.saveAll(titulos);

        gerarEGuardarContrato(m, aluno, autoescolaId, parcelas);
        auditService.registrar("matricula", m.getId(), AcaoAuditoria.CRIACAO, null, MatriculaAuditoria.de(m));
        return detalhe(m);
    }

    @Transactional
    public MatriculaDetalheResponse adicionarAulasAvulsas(Long id, AulaAvulsaRequest req) {
        Matricula m = exigir(id);
        if (m.getStatus() != StatusMatricula.ATIVA) {
            throw new RegraNegocioException("Só é possível adicionar aulas a uma matrícula ativa.");
        }
        LocalDate hoje = configuracaoService.hoje();
        if (req.vencimento().isBefore(hoje)) {
            throw new RegraNegocioException("O vencimento não pode ser no passado.");
        }
        BigDecimal total = req.valorUnitario().multiply(BigDecimal.valueOf(req.quantidade()));

        CreditoAula credito = new CreditoAula();
        credito.setMatriculaId(m.getId());
        credito.setAlunoId(m.getAlunoId());
        credito.setOrigem(OrigemCredito.AVULSA);
        credito.setQuantidade(req.quantidade());
        credito.setValorUnitario(req.valorUnitario());
        credito.setDescricao(req.quantidade() + " aula(s) avulsa(s)");
        creditoRepository.save(credito);

        TituloReceber t = new TituloReceber();
        t.setAlunoId(m.getAlunoId());
        t.setMatriculaId(m.getId());
        t.setOrigem(OrigemTitulo.AVULSA);
        t.setDescricao(req.quantidade() + " aula(s) avulsa(s)");
        t.setNumeroParcela(1);
        t.setTotalParcelas(1);
        t.setValor(total);
        t.setVencimento(req.vencimento());
        t.setStatus(StatusTitulo.ABERTO);
        tituloRepository.save(t);

        auditService.registrar("matricula", id, AcaoAuditoria.ALTERACAO, null, credito.getDescricao() + " — " + total);
        return detalhe(m);
    }

    /**
     * Cancela a matrícula e as parcelas ainda em aberto. Parcelas já pagas continuam registradas:
     * a devolução, se houver, é tratada no financeiro (Fase 4). Aulas agendadas serão canceladas na Fase 3.
     */
    @Transactional
    public MatriculaDetalheResponse cancelar(Long id, String motivo) {
        Matricula m = exigir(id);
        if (m.getStatus() != StatusMatricula.ATIVA) {
            throw new RegraNegocioException("Só é possível cancelar matrículas ativas.");
        }
        var antes = MatriculaAuditoria.de(m);
        m.setStatus(StatusMatricula.CANCELADA);
        m.setMotivoCancelamento(motivo.trim());
        m.setCanceladaEm(clock.instant());
        for (TituloReceber t : tituloRepository.findByMatriculaIdAndStatus(id, StatusTitulo.ABERTO)) {
            t.setStatus(StatusTitulo.CANCELADO);
        }
        auditService.registrar("matricula", id, AcaoAuditoria.ALTERACAO, antes, MatriculaAuditoria.de(m));
        return detalhe(m);
    }

    /** PDF do contrato. Se o arquivo não existir mais no armazenamento, é gerado de novo com os dados da matrícula. */
    @Transactional
    public byte[] contrato(Long id) {
        Matricula m = exigir(id);
        if (m.getContratoPath() != null && storage.existe(m.getContratoPath())) {
            return storage.ler(m.getContratoPath());
        }
        Long autoescolaId = SecurityUtils.autoescolaIdObrigatorio();
        Aluno aluno = alunoService.exigir(m.getAlunoId());
        List<Parcela> parcelas = tituloRepository.findByMatriculaIdOrderByVencimentoAscIdAsc(id).stream()
                .filter(t -> t.getOrigem() == OrigemTitulo.MATRICULA && t.getStatus() != StatusTitulo.CANCELADO)
                .map(t -> new Parcela(t.getNumeroParcela(), t.getDescricao(), t.getValor(), t.getVencimento()))
                .toList();
        return gerarEGuardarContrato(m, aluno, autoescolaId, parcelas);
    }

    private byte[] gerarEGuardarContrato(Matricula m, Aluno aluno, Long autoescolaId, List<Parcela> parcelas) {
        Autoescola ae = autoescolaRepository.findById(autoescolaId)
                .orElseThrow(() -> new NaoEncontradoException("Autoescola não encontrada."));
        ConfiguracaoAutoescola cfg = configuracaoService.configuracaoAtual();
        var dados = new ContratoPdfService.Dados(
                ae.getNome(), Documentos.formatarCnpj(ae.getCnpj()), ae.getCidade(), ae.getUf(),
                aluno.getNome(), Documentos.formatarCpf(aluno.getCpf()), aluno.getRg(), aluno.getDataNascimento(),
                enderecoDe(aluno), aluno.getTelefone(),
                m.getCategoria(), m.getPacoteNome(), m.getAulasPraticas(), cfg.getDuracaoAulaMin(), m.isTeoricoIncluso(),
                m.getValorBruto(), m.getDesconto(), m.getValorTotal(), m.getDataMatricula(), parcelas,
                cfg.getAntecedenciaCancelamentoHoras(), cfg.isCobrarFalta(), cfg.getValorFalta());
        byte[] pdf = contratoPdfService.gerar(dados);
        String caminho = autoescolaId + "/contratos/matricula-" + m.getId() + ".pdf";
        storage.salvar(caminho, pdf);
        m.setContratoPath(caminho);
        return pdf;
    }

    private static String enderecoDe(Aluno a) {
        List<String> partes = new ArrayList<>();
        if (a.getLogradouro() != null) {
            partes.add(a.getLogradouro() + (a.getNumero() != null ? ", " + a.getNumero() : ""));
        }
        if (a.getComplemento() != null) {
            partes.add(a.getComplemento());
        }
        if (a.getBairro() != null) {
            partes.add(a.getBairro());
        }
        if (a.getCidade() != null) {
            partes.add(a.getCidade() + (a.getUf() != null ? "/" + a.getUf() : ""));
        }
        if (a.getCep() != null && a.getCep().length() == 8) {
            partes.add("CEP " + a.getCep().substring(0, 5) + "-" + a.getCep().substring(5));
        }
        return partes.isEmpty() ? null : String.join(", ", partes);
    }

    private MatriculaDetalheResponse detalhe(Matricula m) {
        LocalDate hoje = configuracaoService.hoje();
        String nome = alunoService.nomes(List.of(m.getAlunoId())).get(m.getAlunoId());
        List<CreditoAula> creditos = creditoRepository.findByMatriculaIdOrderByIdAsc(m.getId());
        List<TituloResponse> titulos = tituloRepository.findByMatriculaIdOrderByVencimentoAscIdAsc(m.getId()).stream()
                .map(t -> TituloResponse.de(t, hoje)).toList();
        int aulas = creditos.stream().map(CreditoAula::getQuantidade).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        return new MatriculaDetalheResponse(MatriculaResponse.de(m, nome),
                creditos.stream().map(CreditoResponse::de).collect(Collectors.toList()), titulos, aulas);
    }

    private Matricula exigir(Long id) {
        return Tenants.daAutoescolaAtual(repository.findById(id), "Matrícula não encontrada.");
    }
}
