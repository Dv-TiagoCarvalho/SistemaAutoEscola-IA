package br.com.cfc.shared.config;

import br.com.cfc.autoescola.AutoescolaDtos.AdminInicialRequest;
import br.com.cfc.autoescola.AutoescolaDtos.CriarAutoescolaRequest;
import br.com.cfc.autoescola.AutoescolaRepository;
import br.com.cfc.autoescola.AutoescolaService;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.usuario.UsuarioRepository;
import br.com.cfc.usuario.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cria o SUPER_ADMIN na primeira subida e, se SEED_EXEMPLO=true, dados de exemplo. */
@Component
public class BootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);
    private static final String SENHA_EXEMPLO = "Senha@123";

    private final AppProperties props;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final AutoescolaRepository autoescolaRepository;
    private final AutoescolaService autoescolaService;

    public BootstrapRunner(AppProperties props, UsuarioRepository usuarioRepository, UsuarioService usuarioService,
                           AutoescolaRepository autoescolaRepository, AutoescolaService autoescolaService) {
        this.props = props;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.autoescolaRepository = autoescolaRepository;
        this.autoescolaService = autoescolaService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var b = props.bootstrap();
        if (b.superadminEmail() != null && !b.superadminEmail().isBlank()
                && b.superadminSenha() != null && !b.superadminSenha().isBlank()
                && !usuarioRepository.existsByPerfil(Perfil.SUPER_ADMIN)) {
            usuarioService.novoUsuario(null, "Administrador da Plataforma", b.superadminEmail(), b.superadminSenha(), Perfil.SUPER_ADMIN);
            log.info("SUPER_ADMIN criado: {}", b.superadminEmail());
        }
        if (b.seedExemplo() && autoescolaRepository.count() == 0) {
            var exemplo = autoescolaService.criar(new CriarAutoescolaRequest("CFC Exemplo", "11222333000181", "MG",
                    "Belo Horizonte", "31999990000", "contato@cfcexemplo.com.br",
                    new AdminInicialRequest("Ana Admin", "admin@cfcexemplo.com.br", SENHA_EXEMPLO)));
            usuarioService.novoUsuario(exemplo.id(), "Sara Secretaria", "secretaria@cfcexemplo.com.br", SENHA_EXEMPLO, Perfil.SECRETARIA);
            usuarioService.novoUsuario(exemplo.id(), "Igor Instrutor", "instrutor@cfcexemplo.com.br", SENHA_EXEMPLO, Perfil.INSTRUTOR);
            autoescolaService.criar(new CriarAutoescolaRequest("CFC Modelo", "12ABC34501DE35", "SP",
                    "São Paulo", null, null,
                    new AdminInicialRequest("Marcos Admin", "admin@cfcmodelo.com.br", SENHA_EXEMPLO)));
            log.warn("Dados de EXEMPLO criados (senha {}). Não use SEED_EXEMPLO em produção.", SENHA_EXEMPLO);
        }
    }
}
