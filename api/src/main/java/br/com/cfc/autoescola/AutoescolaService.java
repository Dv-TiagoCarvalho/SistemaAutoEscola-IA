package br.com.cfc.autoescola;

import br.com.cfc.autoescola.AutoescolaDtos.AtualizarAutoescolaRequest;
import br.com.cfc.autoescola.AutoescolaDtos.AutoescolaResponse;
import br.com.cfc.autoescola.AutoescolaDtos.CriarAutoescolaRequest;
import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.exception.NaoEncontradoException;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.shared.validation.Documentos;
import br.com.cfc.shared.web.PaginaResponse;
import br.com.cfc.usuario.Usuario;
import br.com.cfc.usuario.UsuarioService;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Cadastro de autoescolas clientes, feito pelo SUPER_ADMIN da plataforma. */
@Service
public class AutoescolaService {

    /**
     * Regras iniciais por categoria. São só um ponto de partida: a legislação está mudando
     * (ex.: Resolução Contran 1.020/2025) e o ADMIN de cada autoescola deve conferir e ajustar.
     */
    private record RegraPadrao(Categoria categoria, String praticas, String teoricas, int idade) {
    }

    private static final List<RegraPadrao> REGRAS_PADRAO = List.of(
            new RegraPadrao(Categoria.ACC, "2.0", "0.0", 18),
            new RegraPadrao(Categoria.A, "2.0", "0.0", 18),
            new RegraPadrao(Categoria.B, "2.0", "0.0", 18),
            new RegraPadrao(Categoria.AB, "4.0", "0.0", 18),
            new RegraPadrao(Categoria.C, "2.0", "0.0", 19),
            new RegraPadrao(Categoria.D, "2.0", "0.0", 21),
            new RegraPadrao(Categoria.E, "2.0", "0.0", 21));

    private final AutoescolaRepository repository;
    private final ConfiguracaoRepository configuracaoRepository;
    private final UsuarioService usuarioService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public AutoescolaService(AutoescolaRepository repository, ConfiguracaoRepository configuracaoRepository,
                             UsuarioService usuarioService, AuditService auditService,
                             JdbcTemplate jdbcTemplate, Clock clock) {
        this.repository = repository;
        this.configuracaoRepository = configuracaoRepository;
        this.usuarioService = usuarioService;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AutoescolaResponse> listar(String busca, int pagina, int tamanho) {
        String termo = (busca == null || busca.isBlank()) ? null : busca.trim();
        var page = repository.buscar(termo, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 100)));
        return PaginaResponse.de(page.map(AutoescolaResponse::de));
    }

    @Transactional(readOnly = true)
    public AutoescolaResponse buscar(Long id) {
        return AutoescolaResponse.de(carregar(id));
    }

    /** Cria a autoescola, suas configurações padrão, as regras por categoria e o primeiro ADMIN, tudo numa transação. */
    @Transactional
    public AutoescolaResponse criar(CriarAutoescolaRequest req) {
        String cnpj = Documentos.normalizarCnpj(req.cnpj());
        if (repository.existsByCnpj(cnpj)) {
            throw new ConflitoException("Já existe uma autoescola com este CNPJ.");
        }
        Autoescola autoescola = new Autoescola();
        preencher(autoescola, req.nome(), cnpj, req.uf(), req.cidade(), req.telefone(), req.email());
        autoescola.setAtivo(true);
        autoescola = repository.save(autoescola);

        ConfiguracaoAutoescola config = new ConfiguracaoAutoescola();
        config.setAutoescolaId(autoescola.getId());
        config.setAtualizadoEm(clock.instant());
        configuracaoRepository.save(config);

        criarRegrasPadrao(autoescola.getId());

        Usuario admin = usuarioService.novoUsuario(autoescola.getId(), req.admin().nome(), req.admin().email(),
                req.admin().senha(), Perfil.ADMIN);

        var resposta = AutoescolaResponse.de(autoescola);
        auditService.registrar(autoescola.getId(), "autoescola", autoescola.getId(), AcaoAuditoria.CRIACAO, null, resposta);
        auditService.registrar(autoescola.getId(), "usuario", admin.getId(), AcaoAuditoria.CRIACAO, null,
                new AdminCriado(admin.getId(), admin.getNome(), admin.getEmail()));
        return resposta;
    }

    @Transactional
    public AutoescolaResponse atualizar(Long id, AtualizarAutoescolaRequest req) {
        Autoescola autoescola = carregar(id);
        var antes = AutoescolaResponse.de(autoescola);
        String cnpj = Documentos.normalizarCnpj(req.cnpj());
        if (repository.existsByCnpjAndIdNot(cnpj, id)) {
            throw new ConflitoException("Já existe outra autoescola com este CNPJ.");
        }
        preencher(autoescola, req.nome(), cnpj, req.uf(), req.cidade(), req.telefone(), req.email());
        var depois = AutoescolaResponse.de(autoescola);
        auditService.registrar(id, "autoescola", id, AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    /** Suspender bloqueia login e refresh de todos os usuários da autoescola. */
    @Transactional
    public AutoescolaResponse alterarStatus(Long id, boolean ativo) {
        Autoescola autoescola = carregar(id);
        var antes = AutoescolaResponse.de(autoescola);
        autoescola.setAtivo(ativo);
        var depois = AutoescolaResponse.de(autoescola);
        auditService.registrar(id, "autoescola", id, AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    /**
     * Insere via JDBC porque quem cria é o SUPER_ADMIN (ou o seed), fora do contexto da nova autoescola.
     * Assim o autoescola_id é explícito e não depende do filtro de tenant do Hibernate.
     */
    private void criarRegrasPadrao(Long autoescolaId) {
        Timestamp agora = Timestamp.from(Instant.now(clock));
        jdbcTemplate.batchUpdate("""
                        insert into regra_categoria
                          (autoescola_id, categoria, horas_praticas_minimas, horas_teoricas_minimas, idade_minima,
                           ativa, criado_em, atualizado_em, version)
                        values (?, ?, ?, ?, ?, true, ?, ?, 0)
                        """,
                REGRAS_PADRAO, REGRAS_PADRAO.size(), (ps, r) -> {
                    ps.setLong(1, autoescolaId);
                    ps.setString(2, r.categoria().name());
                    ps.setBigDecimal(3, new BigDecimal(r.praticas()));
                    ps.setBigDecimal(4, new BigDecimal(r.teoricas()));
                    ps.setInt(5, r.idade());
                    ps.setTimestamp(6, agora);
                    ps.setTimestamp(7, agora);
                });
    }

    private void preencher(Autoescola a, String nome, String cnpj, String uf, String cidade, String telefone, String email) {
        a.setNome(nome.trim());
        a.setCnpj(cnpj);
        a.setUf(uf);
        a.setCidade(cidade.trim());
        a.setTelefone(telefone == null || telefone.isBlank() ? null : telefone.replaceAll("\\D", ""));
        a.setEmail(email == null || email.isBlank() ? null : email.trim().toLowerCase());
    }

    private Autoescola carregar(Long id) {
        return repository.findById(id).orElseThrow(() -> new NaoEncontradoException("Autoescola não encontrada."));
    }

    private record AdminCriado(Long id, String nome, String email) {
    }
}
