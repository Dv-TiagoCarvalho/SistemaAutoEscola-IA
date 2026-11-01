package br.com.cfc.aluno;

import br.com.cfc.aluno.AlunoDtos.ConsentimentoResponse;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.domain.Tenants;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.web.RequestInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/** Registro do consentimento LGPD do aluno (data, versão do termo e IP). O texto do termo é da autoescola. */
@Service
public class ConsentimentoService {

    private final ConsentimentoRepository repository;
    private final AlunoService alunoService;
    private final AuditService auditService;
    private final Clock clock;

    public ConsentimentoService(ConsentimentoRepository repository, AlunoService alunoService,
                                AuditService auditService, Clock clock) {
        this.repository = repository;
        this.alunoService = alunoService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ConsentimentoResponse> listar(Long alunoId) {
        alunoService.exigir(alunoId);
        return repository.findByAlunoIdOrderByAceitoEmDescIdDesc(alunoId).stream().map(ConsentimentoResponse::de).toList();
    }

    @Transactional
    public ConsentimentoResponse registrar(Long alunoId, String versaoTermo) {
        alunoService.exigir(alunoId);
        String versao = versaoTermo.trim();
        if (repository.existsByAlunoIdAndVersaoTermoAndRevogadoEmIsNull(alunoId, versao)) {
            throw new ConflitoException("O aluno já tem consentimento vigente para a versão " + versao + " do termo.");
        }
        ConsentimentoLgpd c = new ConsentimentoLgpd();
        c.setAlunoId(alunoId);
        c.setVersaoTermo(versao);
        c.setAceitoEm(clock.instant());
        c.setIp(RequestInfo.ipAtual());
        c = repository.save(c);
        var resposta = ConsentimentoResponse.de(c);
        auditService.registrar("consentimento_lgpd", c.getId(), AcaoAuditoria.CRIACAO, null, resposta);
        return resposta;
    }

    @Transactional
    public ConsentimentoResponse revogar(Long id) {
        ConsentimentoLgpd c = Tenants.daAutoescolaAtual(repository.findById(id), "Consentimento não encontrado.");
        if (!c.isVigente()) {
            throw new ConflitoException("Este consentimento já foi revogado.");
        }
        var antes = ConsentimentoResponse.de(c);
        c.setRevogadoEm(clock.instant());
        var depois = ConsentimentoResponse.de(c);
        auditService.registrar("consentimento_lgpd", id, AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }
}
