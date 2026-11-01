package br.com.cfc.agenda;

import br.com.cfc.agenda.AgendaDtos.AulaRequest;
import br.com.cfc.agenda.AgendaDtos.AulaResponse;
import br.com.cfc.agenda.AgendaDtos.CancelarAulaRequest;
import br.com.cfc.agenda.AgendaDtos.RegistroAulaRequest;
import br.com.cfc.agenda.AgendaDtos.SaldoAulasResponse;
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
import br.com.cfc.financeiro.StatusTitulo;
import br.com.cfc.financeiro.TituloReceber;
import br.com.cfc.financeiro.TituloReceberRepository;
import br.com.cfc.matricula.CreditoAula;
import br.com.cfc.matricula.CreditoAulaRepository;
import br.com.cfc.matricula.Matricula;
import br.com.cfc.matricula.MatriculaRepository;
import br.com.cfc.matricula.StatusMatricula;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.domain.Tenants;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.exception.NaoEncontradoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.shared.security.SecurityUtils;
import br.com.cfc.shared.security.UsuarioAutenticado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agenda de aulas práticas. Regras principais:
 * <ul>
 *   <li>instrutor, veículo e aluno não podem ter duas aulas ao mesmo tempo;</li>
 *   <li>a aula consome um crédito da matrícula (pacote + avulsas); sem saldo, não agenda;</li>
 *   <li>duração, antecedências, limite diário e cobrança de falta vêm da configuração da autoescola;</li>
 *   <li>categoria do veículo e do instrutor precisam bater com a do processo do aluno.</li>
 * </ul>
 */
@Service
public class AulaService {

    static final Set<StatusAula> OCUPAM_AGENDA = EnumSet.of(StatusAula.AGENDADA, StatusAula.REALIZADA);
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AulaRepository repository;
    private final MatriculaRepository matriculaRepository;
    private final CreditoAulaRepository creditoRepository;
    private final TituloReceberRepository tituloRepository;
    private final InstrutorRepository instrutorRepository;
    private final VeiculoRepository veiculoRepository;
    private final AlunoRepository alunoRepository;
    private final AlunoService alunoService;
    private final ProcessoService processoService;
    private final ConfiguracaoService configuracaoService;
    private final AuditService auditService;
    private final Clock clock;

    public AulaService(AulaRepository repository, MatriculaRepository matriculaRepository,
                       CreditoAulaRepository creditoRepository, TituloReceberRepository tituloRepository,
                       InstrutorRepository instrutorRepository, VeiculoRepository veiculoRepository,
                       AlunoRepository alunoRepository, AlunoService alunoService, ProcessoService processoService,
                       ConfiguracaoService configuracaoService, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.matriculaRepository = matriculaRepository;
        this.creditoRepository = creditoRepository;
        this.tituloRepository = tituloRepository;
        this.instrutorRepository = instrutorRepository;
        this.veiculoRepository = veiculoRepository;
        this.alunoRepository = alunoRepository;
        this.alunoService = alunoService;
        this.processoService = processoService;
        this.configuracaoService = configuracaoService;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ---------- Agendar ----------

    @Transactional
    public AulaResponse agendar(AulaRequest req) {
        SecurityUtils.autoescolaIdObrigatorio();
        ConfiguracaoAutoescola cfg = configuracaoService.configuracaoAtual();
        ZoneId zona = ZoneId.of(cfg.getFusoHorario());
        Instant inicio = req.inicio().atZone(zona).toInstant();
        Instant fim = inicio.plus(Duration.ofMinutes(cfg.getDuracaoAulaMin()));
        Instant agora = clock.instant();

        if (inicio.isBefore(agora.plus(Duration.ofHours(cfg.getAntecedenciaAgendamentoHoras())))) {
            throw new RegraNegocioException("A aula precisa ser agendada com pelo menos "
                    + cfg.getAntecedenciaAgendamentoHoras() + " hora(s) de antecedência.");
        }

        // Ordem fixa de travas (matrícula, instrutor, veículo) para não dar deadlock entre agendamentos simultâneos.
        Matricula matricula = Tenants.daAutoescolaAtual(matriculaRepository.buscarComLock(req.matriculaId()), "Matrícula não encontrada.");
        Instrutor instrutor = Tenants.daAutoescolaAtual(instrutorRepository.buscarComLock(req.instrutorId()), "Instrutor não encontrado.");
        Veiculo veiculo = Tenants.daAutoescolaAtual(veiculoRepository.buscarComLock(req.veiculoId()), "Veículo não encontrado.");

        if (matricula.getStatus() != StatusMatricula.ATIVA) {
            throw new RegraNegocioException("A matrícula não está ativa.");
        }
        Aluno aluno = alunoService.exigir(matricula.getAlunoId());
        if (!aluno.isAtivo()) {
            throw new RegraNegocioException("Aluno inativo.");
        }
        ProcessoHabilitacao processo = processoService.exigir(matricula.getProcessoId());
        validarProcesso(processo, cfg, inicio.atZone(zona).toLocalDate());

        if (!instrutor.isAtivo()) {
            throw new RegraNegocioException("O instrutor está inativo.");
        }
        if (!veiculo.isAtivo()) {
            throw new RegraNegocioException("O veículo está inativo.");
        }
        validarCategorias(processo.getCategoria(), instrutor, veiculo);
        LocalDate dataAula = inicio.atZone(zona).toLocalDate();
        if (instrutor.getCnhValidade() != null && dataAula.isAfter(instrutor.getCnhValidade())) {
            throw new RegraNegocioException("A CNH do instrutor estará vencida na data da aula.");
        }

        if (repository.conflitoInstrutor(instrutor.getId(), inicio, fim, OCUPAM_AGENDA)) {
            throw new ConflitoException("O instrutor já tem uma aula nesse horário.");
        }
        if (repository.conflitoVeiculo(veiculo.getId(), inicio, fim, OCUPAM_AGENDA)) {
            throw new ConflitoException("O veículo já está em uso nesse horário.");
        }
        if (repository.conflitoAluno(aluno.getId(), inicio, fim, OCUPAM_AGENDA)) {
            throw new ConflitoException("O aluno já tem uma aula nesse horário.");
        }

        Instant diaIni = dataAula.atStartOfDay(zona).toInstant();
        Instant diaFim = dataAula.plusDays(1).atStartOfDay(zona).toInstant();
        long noDia = repository.contarDoAlunoNoPeriodo(aluno.getId(), diaIni, diaFim, OCUPAM_AGENDA);
        if (noDia >= cfg.getMaxAulasDiaAluno()) {
            throw new RegraNegocioException("O aluno já atingiu o limite de " + cfg.getMaxAulasDiaAluno() + " aula(s) por dia.");
        }

        SaldoAulasResponse saldo = calcularSaldo(matricula.getId(), cfg);
        if (saldo.saldo() <= 0) {
            throw new RegraNegocioException("A matrícula não tem aulas disponíveis. Adicione aulas avulsas para continuar.");
        }

        Aula a = new Aula();
        a.setAlunoId(aluno.getId());
        a.setMatriculaId(matricula.getId());
        a.setInstrutorId(instrutor.getId());
        a.setVeiculoId(veiculo.getId());
        a.setInicio(inicio);
        a.setFim(fim);
        a.setStatus(StatusAula.AGENDADA);
        a = repository.save(a);
        var resposta = AulaResponse.de(a, aluno.getNome(), instrutor.getNome(), veiculo.getPlaca());
        auditService.registrar("aula", a.getId(), AcaoAuditoria.CRIACAO, null, resposta);
        return resposta;
    }

    private void validarProcesso(ProcessoHabilitacao p, ConfiguracaoAutoescola cfg, LocalDate dataAula) {
        if (p.getSituacao() == SituacaoProcesso.APROVADO || p.getSituacao() == SituacaoProcesso.DESISTENTE) {
            throw new RegraNegocioException("O processo do aluno está encerrado.");
        }
        if (cfg.isExigirExamesParaAulas()
                && (p.getExameMedico() != StatusExame.APTO || p.getExamePsicologico() != StatusExame.APTO)) {
            throw new RegraNegocioException("O aluno precisa estar APTO nos exames médico e psicológico para ter aulas práticas.");
        }
        if (p.getLadvValidade() != null && dataAula.isAfter(p.getLadvValidade())) {
            throw new RegraNegocioException("A LADV do aluno estará vencida na data da aula.");
        }
    }

    /** Categorias aceitas no veículo para um processo: AB aceita veículo A ou B. */
    static Set<Categoria> categoriasDeVeiculoAceitas(Categoria processo) {
        return processo == Categoria.AB ? EnumSet.of(Categoria.A, Categoria.B) : EnumSet.of(processo);
    }

    private void validarCategorias(Categoria processo, Instrutor instrutor, Veiculo veiculo) {
        if (!categoriasDeVeiculoAceitas(processo).contains(veiculo.getCategoria())) {
            throw new RegraNegocioException("O veículo é da categoria " + veiculo.getCategoria()
                    + ", incompatível com o processo do aluno (" + processo + ").");
        }
        if (!instrutor.categoriasSet().contains(veiculo.getCategoria())) {
            throw new RegraNegocioException("O instrutor não está habilitado para ensinar a categoria " + veiculo.getCategoria() + ".");
        }
    }

    // ---------- Saldo ----------

    @Transactional(readOnly = true)
    public SaldoAulasResponse saldo(Long matriculaId) {
        Matricula m = Tenants.daAutoescolaAtual(matriculaRepository.findById(matriculaId), "Matrícula não encontrada.");
        return calcularSaldo(m.getId(), configuracaoService.configuracaoAtual());
    }

    /**
     * Aulas agendadas e realizadas consomem crédito. A falta consome crédito só quando a autoescola
     * NÃO cobra falta (se cobra, o aluno paga a taxa e mantém o crédito).
     */
    SaldoAulasResponse calcularSaldo(Long matriculaId, ConfiguracaoAutoescola cfg) {
        int contratadas = creditoRepository.findByMatriculaIdOrderByIdAsc(matriculaId).stream()
                .mapToInt(CreditoAula::getQuantidade).sum();
        Set<StatusAula> consomem = EnumSet.of(StatusAula.AGENDADA, StatusAula.REALIZADA);
        if (!cfg.isCobrarFalta()) {
            consomem.add(StatusAula.FALTA);
        }
        int consumidas = (int) repository.contarDaMatricula(matriculaId, consomem);
        return new SaldoAulasResponse(matriculaId, contratadas, consumidas, contratadas - consumidas);
    }

    // ---------- Cancelar ----------

    @Transactional
    public AulaResponse cancelar(Long id, CancelarAulaRequest req) {
        UsuarioAutenticado usuario = SecurityUtils.usuarioObrigatorio();
        Aula a = exigir(id);
        if (usuario.perfil() == Perfil.ALUNO) {
            Aluno proprio = alunoRepository.findByUsuarioId(usuario.id()).orElse(null);
            if (proprio == null || !proprio.getId().equals(a.getAlunoId())) {
                throw new NaoEncontradoException("Aula não encontrada.");
            }
        }
        if (a.getStatus() != StatusAula.AGENDADA) {
            throw new RegraNegocioException("Só é possível cancelar aulas agendadas.");
        }
        ConfiguracaoAutoescola cfg = configuracaoService.configuracaoAtual();
        Instant agora = clock.instant();
        if (!agora.isBefore(a.getInicio())) {
            throw new RegraNegocioException("A aula já começou. Registre como realizada ou falta.");
        }
        var antes = resposta(a);
        boolean tardio = agora.isAfter(a.getInicio().minus(Duration.ofHours(cfg.getAntecedenciaCancelamentoHoras())));
        boolean equipe = usuario.perfil() != Perfil.ALUNO;
        boolean isentar = equipe && Boolean.TRUE.equals(req.isentarCobranca());

        a.setStatus(StatusAula.CANCELADA);
        a.setMotivoCancelamento(req.motivo().trim());
        a.setCancelamentoTardio(tardio);
        if (tardio && !isentar) {
            gerarTituloDeFalta(a, cfg, "Cancelamento tardio");
        }
        var depois = resposta(a);
        auditService.registrar("aula", a.getId(), AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    // ---------- Registro da aula ----------

    @Transactional
    public AulaResponse registrar(Long id, RegistroAulaRequest req) {
        UsuarioAutenticado usuario = SecurityUtils.usuarioObrigatorio();
        Aula a = exigir(id);
        if (usuario.perfil() == Perfil.INSTRUTOR) {
            Instrutor proprio = instrutorRepository.findByUsuarioId(usuario.id()).orElse(null);
            if (proprio == null || !proprio.getId().equals(a.getInstrutorId())) {
                throw new NaoEncontradoException("Aula não encontrada.");
            }
        }
        if (a.getStatus() != StatusAula.AGENDADA) {
            throw new RegraNegocioException("Esta aula já foi registrada ou cancelada.");
        }
        if (req.resultado() != StatusAula.REALIZADA && req.resultado() != StatusAula.FALTA) {
            throw new RegraNegocioException("O resultado deve ser REALIZADA ou FALTA.");
        }
        Instant agora = clock.instant();
        if (agora.isBefore(a.getInicio())) {
            throw new RegraNegocioException("A aula ainda não começou.");
        }
        if (req.kmInicial() != null && req.kmFinal() != null && req.kmFinal() < req.kmInicial()) {
            throw new RegraNegocioException("O km final não pode ser menor que o km inicial.");
        }
        var antes = resposta(a);
        ConfiguracaoAutoescola cfg = configuracaoService.configuracaoAtual();
        a.setStatus(req.resultado());
        a.setKmInicial(req.kmInicial());
        a.setKmFinal(req.kmFinal());
        a.setConteudo(vazioParaNulo(req.conteudo()));
        a.setObservacoes(vazioParaNulo(req.observacoes()));
        a.setNota(req.nota());
        a.setRegistradaEm(agora);
        a.setRegistradaPor(usuario.id());

        if (req.resultado() == StatusAula.FALTA) {
            gerarTituloDeFalta(a, cfg, "Falta");
        } else {
            Matricula m = matriculaRepository.findById(a.getMatriculaId()).orElseThrow();
            ProcessoHabilitacao p = processoService.exigirComLock(m.getProcessoId());
            if (p.getSituacao() == SituacaoProcesso.MATRICULADO) {
                p.setSituacao(SituacaoProcesso.EM_AULAS);
            }
        }
        var depois = resposta(a);
        auditService.registrar("aula", a.getId(), AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    private void gerarTituloDeFalta(Aula a, ConfiguracaoAutoescola cfg, String motivo) {
        if (!cfg.isCobrarFalta() || cfg.getValorFalta() == null || cfg.getValorFalta().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        TituloReceber t = new TituloReceber();
        t.setAlunoId(a.getAlunoId());
        t.setMatriculaId(a.getMatriculaId());
        t.setOrigem(OrigemTitulo.FALTA);
        t.setDescricao(motivo + " - aula de " + a.getInicio().atZone(ZoneId.of(cfg.getFusoHorario())).format(DATA_HORA));
        t.setNumeroParcela(1);
        t.setTotalParcelas(1);
        t.setValor(cfg.getValorFalta());
        t.setVencimento(configuracaoService.hoje().plusDays(7));
        t.setStatus(StatusTitulo.ABERTO);
        tituloRepository.save(t);
    }

    // ---------- Consulta ----------

    /** ADMIN/SECRETARIA veem tudo; INSTRUTOR vê só as próprias aulas; ALUNO vê só as próprias. */
    @Transactional(readOnly = true)
    public List<AulaResponse> listar(LocalDate de, LocalDate ate, Long instrutorId, Long alunoId, Long veiculoId, StatusAula status) {
        UsuarioAutenticado usuario = SecurityUtils.usuarioObrigatorio();
        SecurityUtils.autoescolaIdObrigatorio();
        ConfiguracaoAutoescola cfg = configuracaoService.configuracaoAtual();
        ZoneId zona = ZoneId.of(cfg.getFusoHorario());
        LocalDate hoje = configuracaoService.hoje();
        LocalDate d = de == null ? hoje : de;
        LocalDate f = ate == null ? d.plusDays(6) : ate;
        if (f.isBefore(d)) {
            throw new RegraNegocioException("A data final não pode ser anterior à inicial.");
        }
        if (Duration.between(d.atStartOfDay(), f.atStartOfDay()).toDays() > 62) {
            throw new RegraNegocioException("Consulte no máximo 62 dias por vez.");
        }
        if (usuario.perfil() == Perfil.INSTRUTOR) {
            Instrutor proprio = instrutorRepository.findByUsuarioId(usuario.id()).orElse(null);
            if (proprio == null) {
                return List.of();
            }
            instrutorId = proprio.getId();
        } else if (usuario.perfil() == Perfil.ALUNO) {
            Aluno proprio = alunoRepository.findByUsuarioId(usuario.id()).orElse(null);
            if (proprio == null) {
                return List.of();
            }
            alunoId = proprio.getId();
        }
        List<Aula> aulas = repository.buscar(d.atStartOfDay(zona).toInstant(), f.plusDays(1).atStartOfDay(zona).toInstant(),
                instrutorId, alunoId, veiculoId, status);
        return montar(aulas);
    }

    private List<AulaResponse> montar(List<Aula> aulas) {
        if (aulas.isEmpty()) {
            return List.of();
        }
        Map<Long, String> alunos = alunoService.nomes(aulas.stream().map(Aula::getAlunoId).distinct().toList());
        Map<Long, String> instrutores = instrutorRepository.findByIdIn(aulas.stream().map(Aula::getInstrutorId).distinct().toList())
                .stream().collect(Collectors.toMap(Instrutor::getId, Instrutor::getNome));
        Map<Long, String> placas = veiculoRepository.findByIdIn(aulas.stream().map(Aula::getVeiculoId).distinct().toList())
                .stream().collect(Collectors.toMap(Veiculo::getId, Veiculo::getPlaca, (x, y) -> x, java.util.HashMap::new));
        return aulas.stream().map(a -> AulaResponse.de(a, alunos.get(a.getAlunoId()),
                instrutores.get(a.getInstrutorId()), placas.get(a.getVeiculoId()))).toList();
    }

    private AulaResponse resposta(Aula a) {
        return montar(List.of(a)).get(0);
    }

    @Transactional(readOnly = true)
    public AulaResponse buscar(Long id) {
        return resposta(exigir(id));
    }

    Aula exigir(Long id) {
        return Tenants.daAutoescolaAtual(repository.findById(id), "Aula não encontrada.");
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
