package br.com.cfc.usuario;

import br.com.cfc.auth.RefreshTokenRepository;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.exception.NaoEncontradoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.shared.security.SecurityUtils;
import br.com.cfc.shared.security.UsuarioAutenticado;
import br.com.cfc.usuario.UsuarioDtos.AtualizarUsuarioRequest;
import br.com.cfc.usuario.UsuarioDtos.CriarUsuarioRequest;
import br.com.cfc.usuario.UsuarioDtos.UsuarioAuditoria;
import br.com.cfc.usuario.UsuarioDtos.UsuarioResponse;
import br.com.cfc.shared.web.PaginaResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;

/**
 * Gestão da equipe da autoescola (ADMIN, SECRETARIA, INSTRUTOR).
 * Usuários ALUNO são criados junto com o cadastro do aluno (Fase 2).
 * Todas as operações ficam restritas à autoescola do usuário logado.
 */
@Service
public class UsuarioService {

    /** Perfis que o ADMIN pode criar/atribuir por esta tela. */
    static final Set<Perfil> PERFIS_EQUIPE = EnumSet.of(Perfil.ADMIN, Perfil.SECRETARIA, Perfil.INSTRUTOR);

    private final UsuarioRepository repository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    public UsuarioService(UsuarioRepository repository, RefreshTokenRepository refreshTokenRepository,
                          PasswordEncoder passwordEncoder, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioResponse> listar(String busca, int pagina, int tamanho) {
        Long autoescolaId = SecurityUtils.autoescolaIdObrigatorio();
        String termo = (busca == null || busca.isBlank()) ? null : busca.trim();
        var page = repository.buscar(autoescolaId, PERFIS_EQUIPE, termo,
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 100)));
        return PaginaResponse.de(page.map(UsuarioResponse::de));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscar(Long id) {
        return UsuarioResponse.de(carregarDaAutoescola(id));
    }

    @Transactional
    public UsuarioResponse criar(CriarUsuarioRequest req) {
        Long autoescolaId = SecurityUtils.autoescolaIdObrigatorio();
        validarPerfilDeEquipe(req.perfil());
        Usuario usuario = novoUsuario(autoescolaId, req.nome(), req.email(), req.senha(), req.perfil());
        auditService.registrar("usuario", usuario.getId(), AcaoAuditoria.CRIACAO, null, UsuarioAuditoria.de(usuario));
        return UsuarioResponse.de(usuario);
    }

    /**
     * Cria e salva um usuário. Usado também pelo cadastro de autoescola (primeiro ADMIN)
     * e, nas próximas fases, pelo cadastro de aluno e de instrutor.
     */
    @Transactional
    public Usuario novoUsuario(Long autoescolaId, String nome, String email, String senha, Perfil perfil) {
        String emailNormalizado = Usuario.normalizarEmail(email);
        if (repository.existsByEmail(emailNormalizado)) {
            throw new ConflitoException("Já existe um usuário com este e-mail.");
        }
        Usuario usuario = new Usuario();
        usuario.setAutoescolaId(autoescolaId);
        usuario.setNome(nome.trim());
        usuario.setEmail(emailNormalizado);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        return repository.save(usuario);
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, AtualizarUsuarioRequest req) {
        UsuarioAutenticado logado = SecurityUtils.usuarioObrigatorio();
        Usuario usuario = carregarDaAutoescola(id);
        validarPerfilDeEquipe(usuario.getPerfil());
        validarPerfilDeEquipe(req.perfil());
        var antes = UsuarioAuditoria.de(usuario);

        boolean ehProprioUsuario = usuario.getId().equals(logado.id());
        if (ehProprioUsuario && (!req.ativo() || req.perfil() != usuario.getPerfil())) {
            throw new RegraNegocioException("Você não pode desativar nem alterar o perfil do próprio usuário.");
        }
        boolean deixaDeSerAdminAtivo = usuario.getPerfil() == Perfil.ADMIN && usuario.isAtivo()
                && (req.perfil() != Perfil.ADMIN || !req.ativo());
        if (deixaDeSerAdminAtivo
                && repository.countByAutoescolaIdAndPerfilAndAtivoTrue(usuario.getAutoescolaId(), Perfil.ADMIN) <= 1) {
            throw new RegraNegocioException("A autoescola precisa ter pelo menos um administrador ativo.");
        }

        usuario.setNome(req.nome().trim());
        usuario.setPerfil(req.perfil());
        usuario.setAtivo(req.ativo());
        if (!usuario.isAtivo() || antes.perfil() != usuario.getPerfil()) {
            // Força novo login: o perfil antigo continua no access token por no máximo alguns minutos.
            refreshTokenRepository.revogarTodosDoUsuario(usuario.getId(), clock.instant());
        }
        auditService.registrar("usuario", usuario.getId(), AcaoAuditoria.ALTERACAO, antes, UsuarioAuditoria.de(usuario));
        return UsuarioResponse.de(usuario);
    }

    @Transactional
    public void redefinirSenha(Long id, String novaSenha) {
        Usuario usuario = carregarDaAutoescola(id);
        validarPerfilDeEquipe(usuario.getPerfil());
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        refreshTokenRepository.revogarTodosDoUsuario(usuario.getId(), clock.instant());
        auditService.registrar("usuario", usuario.getId(), AcaoAuditoria.SENHA, null, null);
    }

    /** Usado pelo cadastro de aluno: redefine a senha de acesso do aluno ao app. */
    @Transactional
    public void redefinirSenhaDeAluno(Long usuarioId, String novaSenha) {
        Usuario usuario = carregarAluno(usuarioId);
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        refreshTokenRepository.revogarTodosDoUsuario(usuario.getId(), clock.instant());
        auditService.registrar("usuario", usuario.getId(), AcaoAuditoria.SENHA, null, null);
    }

    /** Usado pelo cadastro de aluno: aluno inativo não consegue entrar no app. */
    @Transactional
    public void definirAtivoDeAluno(Long usuarioId, boolean ativo) {
        Usuario usuario = carregarAluno(usuarioId);
        usuario.setAtivo(ativo);
        if (!ativo) {
            refreshTokenRepository.revogarTodosDoUsuario(usuario.getId(), clock.instant());
        }
    }

    /** Usado pelo cadastro de instrutor: instrutor inativo não consegue entrar no app. */
    @Transactional
    public void definirAtivoDeInstrutor(Long usuarioId, boolean ativo) {
        Usuario usuario = carregarDaAutoescola(usuarioId);
        if (usuario.getPerfil() != Perfil.INSTRUTOR) {
            throw new RegraNegocioException("O usuário informado não é um instrutor.");
        }
        usuario.setAtivo(ativo);
        if (!ativo) {
            refreshTokenRepository.revogarTodosDoUsuario(usuario.getId(), clock.instant());
        }
    }

    private Usuario carregarAluno(Long usuarioId) {
        Usuario usuario = carregarDaAutoescola(usuarioId);
        if (usuario.getPerfil() != Perfil.ALUNO) {
            throw new RegraNegocioException("O usuário informado não é um aluno.");
        }
        return usuario;
    }

    private Usuario carregarDaAutoescola(Long id) {
        Long autoescolaId = SecurityUtils.autoescolaIdObrigatorio();
        return repository.findByIdAndAutoescolaId(id, autoescolaId)
                .orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
    }

    private void validarPerfilDeEquipe(Perfil perfil) {
        if (!PERFIS_EQUIPE.contains(perfil)) {
            throw new RegraNegocioException("Por aqui só é possível gerenciar os perfis ADMIN, SECRETARIA e INSTRUTOR.");
        }
    }
}
