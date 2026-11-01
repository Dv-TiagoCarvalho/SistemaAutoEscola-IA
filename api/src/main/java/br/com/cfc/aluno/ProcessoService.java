package br.com.cfc.aluno;

import br.com.cfc.aluno.AlunoDtos.ProcessoRequest;
import br.com.cfc.aluno.AlunoDtos.ProcessoResponse;
import br.com.cfc.aluno.AlunoDtos.ProcessoUpdateRequest;
import br.com.cfc.autoescola.ConfiguracaoService;
import br.com.cfc.autoescola.RegraCategoria;
import br.com.cfc.autoescola.RegraCategoriaRepository;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.domain.Tenants;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.exception.NaoEncontradoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class ProcessoService {

    public static final Set<SituacaoProcesso> ENCERRADAS = EnumSet.of(SituacaoProcesso.APROVADO, SituacaoProcesso.DESISTENTE);

    private final ProcessoRepository repository;
    private final AlunoService alunoService;
    private final RegraCategoriaRepository regraRepository;
    private final ConfiguracaoService configuracaoService;
    private final AuditService auditService;

    public ProcessoService(ProcessoRepository repository, AlunoService alunoService,
                           RegraCategoriaRepository regraRepository, ConfiguracaoService configuracaoService,
                           AuditService auditService) {
        this.repository = repository;
        this.alunoService = alunoService;
        this.regraRepository = regraRepository;
        this.configuracaoService = configuracaoService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ProcessoResponse> listar(Long alunoId) {
        alunoService.exigir(alunoId);
        LocalDate hoje = configuracaoService.hoje();
        return repository.findByAlunoIdOrderByCriadoEmDescIdDesc(alunoId).stream()
                .map(p -> ProcessoResponse.de(p, hoje)).toList();
    }

    @Transactional
    public ProcessoResponse criar(Long alunoId, ProcessoRequest req) {
        Aluno aluno = alunoService.exigir(alunoId);
        if (!aluno.isAtivo()) {
            throw new RegraNegocioException("Aluno inativo. Reative o cadastro para abrir um processo.");
        }
        RegraCategoria regra = regraRepository.findByCategoria(req.categoria())
                .orElseThrow(() -> new NaoEncontradoException("Regra da categoria " + req.categoria() + " não encontrada."));
        if (!regra.isAtiva()) {
            throw new RegraNegocioException("A categoria " + req.categoria() + " não está ativa nesta autoescola.");
        }
        LocalDate hoje = configuracaoService.hoje();
        int idade = Period.between(aluno.getDataNascimento(), hoje).getYears();
        if (idade < regra.getIdadeMinima()) {
            throw new RegraNegocioException("A idade mínima para a categoria " + req.categoria() + " é "
                    + regra.getIdadeMinima() + " anos (o aluno tem " + idade + ").");
        }
        if (repository.existsByAlunoIdAndCategoriaAndSituacaoNotIn(alunoId, req.categoria(), ENCERRADAS)) {
            throw new ConflitoException("Este aluno já tem um processo em andamento na categoria " + req.categoria() + ".");
        }

        ProcessoHabilitacao p = new ProcessoHabilitacao();
        p.setAlunoId(alunoId);
        p.setCategoria(req.categoria());
        p.setTipo(req.tipo());
        p.setRenach(normalizarRenach(req.renach()));
        p.setObservacoes(vazioParaNulo(req.observacoes()));
        p = repository.save(p);
        var resposta = ProcessoResponse.de(p, hoje);
        auditService.registrar("processo_habilitacao", p.getId(), AcaoAuditoria.CRIACAO, null, resposta);
        return resposta;
    }

    @Transactional
    public ProcessoResponse atualizar(Long id, ProcessoUpdateRequest req) {
        ProcessoHabilitacao p = exigir(id);
        if (ENCERRADAS.contains(p.getSituacao())) {
            throw new RegraNegocioException("Este processo está encerrado (" + p.getSituacao() + ") e não pode ser alterado.");
        }
        LocalDate hoje = configuracaoService.hoje();
        var antes = ProcessoResponse.de(p, hoje);
        p.setRenach(normalizarRenach(req.renach()));
        p.setExameMedico(req.exameMedico());
        p.setExamePsicologico(req.examePsicologico());
        p.setLadvNumero(vazioParaNulo(req.ladvNumero()));
        p.setLadvValidade(req.ladvValidade());
        p.setSituacao(req.situacao());
        p.setObservacoes(vazioParaNulo(req.observacoes()));
        var depois = ProcessoResponse.de(p, hoje);
        auditService.registrar("processo_habilitacao", id, AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    public ProcessoHabilitacao exigir(Long id) {
        return Tenants.daAutoescolaAtual(repository.findById(id), "Processo não encontrado.");
    }

    /** Igual a {@link #exigir}, mas trava a linha até o fim da transação. */
    public ProcessoHabilitacao exigirComLock(Long id) {
        return Tenants.daAutoescolaAtual(repository.buscarComLock(id), "Processo não encontrado.");
    }

    private static String normalizarRenach(String renach) {
        return renach == null || renach.isBlank() ? null : renach.trim().toUpperCase();
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
