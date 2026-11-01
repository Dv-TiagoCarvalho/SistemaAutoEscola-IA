package br.com.cfc.aluno;

import br.com.cfc.aluno.AlunoDtos.AcessoRequest;
import br.com.cfc.aluno.AlunoDtos.AlunoAuditoria;
import br.com.cfc.aluno.AlunoDtos.AlunoRequest;
import br.com.cfc.aluno.AlunoDtos.AlunoResponse;
import br.com.cfc.aluno.AlunoDtos.AlunoResumo;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.domain.Tenants;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.shared.security.SecurityUtils;
import br.com.cfc.shared.validation.Documentos;
import br.com.cfc.shared.web.PaginaResponse;
import br.com.cfc.usuario.Usuario;
import br.com.cfc.usuario.UsuarioService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AlunoService {

    private final AlunoRepository repository;
    private final ProcessoRepository processoRepository;
    private final UsuarioService usuarioService;
    private final AuditService auditService;

    public AlunoService(AlunoRepository repository, ProcessoRepository processoRepository,
                        UsuarioService usuarioService, AuditService auditService) {
        this.repository = repository;
        this.processoRepository = processoRepository;
        this.usuarioService = usuarioService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AlunoResumo> listar(String busca, SituacaoProcesso situacao, boolean incluirInativos,
                                              int pagina, int tamanho) {
        SecurityUtils.autoescolaIdObrigatorio();
        String termo = (busca == null || busca.isBlank()) ? null : busca.trim();
        String digitos = termo == null ? null : termo.replaceAll("\\D", "");
        if (digitos != null && digitos.isEmpty()) {
            digitos = null;
        }
        var page = repository.buscar(termo, digitos, situacao, incluirInativos,
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 100)));

        List<Long> ids = page.getContent().stream().map(Aluno::getId).toList();
        Map<Long, SituacaoProcesso> situacoes = new HashMap<>();
        if (!ids.isEmpty()) {
            // Ordenado do mais novo para o mais antigo: o primeiro de cada aluno é o processo atual.
            for (ProcessoHabilitacao p : processoRepository.findByAlunoIdInOrderByCriadoEmDescIdDesc(ids)) {
                situacoes.putIfAbsent(p.getAlunoId(), p.getSituacao());
            }
        }
        return PaginaResponse.de(page.map(a -> AlunoResumo.de(a, situacoes.get(a.getId()))));
    }

    @Transactional(readOnly = true)
    public AlunoResponse buscar(Long id) {
        return AlunoResponse.de(exigir(id));
    }

    @Transactional
    public AlunoResponse criar(AlunoRequest req) {
        SecurityUtils.autoescolaIdObrigatorio();
        String cpf = Documentos.normalizarCpf(req.cpf());
        if (repository.existsByCpf(cpf)) {
            throw new ConflitoException("Já existe um aluno cadastrado com este CPF.");
        }
        Aluno aluno = new Aluno();
        preencher(aluno, req);
        aluno.setCpf(cpf);
        aluno.setAtivo(true);
        aluno = repository.save(aluno);
        auditService.registrar("aluno", aluno.getId(), AcaoAuditoria.CRIACAO, null, AlunoAuditoria.de(aluno));
        return AlunoResponse.de(aluno);
    }

    @Transactional
    public AlunoResponse atualizar(Long id, AlunoRequest req) {
        Aluno aluno = exigir(id);
        var antes = AlunoAuditoria.de(aluno);
        String cpf = Documentos.normalizarCpf(req.cpf());
        if (!cpf.equals(aluno.getCpf()) && repository.existsByCpfAndIdNot(cpf, id)) {
            throw new ConflitoException("Já existe outro aluno cadastrado com este CPF.");
        }
        preencher(aluno, req);
        aluno.setCpf(cpf);
        auditService.registrar("aluno", id, AcaoAuditoria.ALTERACAO, antes, AlunoAuditoria.de(aluno));
        return AlunoResponse.de(aluno);
    }

    /** Aluno inativo some da lista padrão e perde o acesso ao app. Os dados são mantidos (histórico e financeiro). */
    @Transactional
    public AlunoResponse alterarStatus(Long id, boolean ativo) {
        Aluno aluno = exigir(id);
        var antes = AlunoAuditoria.de(aluno);
        aluno.setAtivo(ativo);
        if (aluno.getUsuarioId() != null) {
            usuarioService.definirAtivoDeAluno(aluno.getUsuarioId(), ativo);
        }
        auditService.registrar("aluno", id, AcaoAuditoria.ALTERACAO, antes, AlunoAuditoria.de(aluno));
        return AlunoResponse.de(aluno);
    }

    @Transactional
    public AlunoResponse criarAcesso(Long id, AcessoRequest req) {
        Long autoescolaId = SecurityUtils.autoescolaIdObrigatorio();
        Aluno aluno = exigir(id);
        if (!aluno.isAtivo()) {
            throw new RegraNegocioException("Reative o aluno antes de criar o acesso ao app.");
        }
        if (aluno.getUsuarioId() != null) {
            throw new ConflitoException("Este aluno já tem acesso ao app.");
        }
        var antes = AlunoAuditoria.de(aluno);
        Usuario usuario = usuarioService.novoUsuario(autoescolaId, aluno.getNome(), req.email(), req.senha(), Perfil.ALUNO);
        aluno.setUsuarioId(usuario.getId());
        auditService.registrar("aluno", id, AcaoAuditoria.ALTERACAO, antes, AlunoAuditoria.de(aluno));
        return AlunoResponse.de(aluno);
    }

    @Transactional
    public void redefinirSenhaDeAcesso(Long id, String novaSenha) {
        Aluno aluno = exigir(id);
        if (aluno.getUsuarioId() == null) {
            throw new RegraNegocioException("Este aluno ainda não tem acesso ao app.");
        }
        usuarioService.redefinirSenhaDeAluno(aluno.getUsuarioId(), novaSenha);
    }

    /** Nomes por id, para telas que listam registros de vários alunos. */
    @Transactional(readOnly = true)
    public Map<Long, String> nomes(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream().collect(Collectors.toMap(Aluno::getId, Aluno::getNome));
    }

    /** Carrega o aluno conferindo que é da autoescola do usuário logado. */
    public Aluno exigir(Long id) {
        return Tenants.daAutoescolaAtual(repository.findById(id), "Aluno não encontrado.");
    }

    private void preencher(Aluno a, AlunoRequest req) {
        String telefone = req.telefone().replaceAll("\\D", "");
        if (telefone.length() < 10 || telefone.length() > 11) {
            throw new RegraNegocioException("Telefone inválido. Informe o DDD e o número.");
        }
        if (req.dataNascimento().getYear() < 1900) {
            throw new RegraNegocioException("Data de nascimento inválida.");
        }
        a.setNome(req.nome().trim());
        a.setRg(vazioParaNulo(req.rg()));
        a.setDataNascimento(req.dataNascimento());
        a.setTelefone(telefone);
        a.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().trim().toLowerCase());
        a.setCep(vazioParaNulo(req.cep()));
        a.setLogradouro(vazioParaNulo(req.logradouro()));
        a.setNumero(vazioParaNulo(req.numero()));
        a.setComplemento(vazioParaNulo(req.complemento()));
        a.setBairro(vazioParaNulo(req.bairro()));
        a.setCidade(vazioParaNulo(req.cidade()));
        a.setUf(vazioParaNulo(req.uf()));
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
