package br.com.cfc.usuario;

import br.com.cfc.TestSupport;
import br.com.cfc.auth.RefreshTokenRepository;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.exception.NaoEncontradoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.usuario.UsuarioDtos.AtualizarUsuarioRequest;
import br.com.cfc.usuario.UsuarioDtos.CriarUsuarioRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsuarioServiceTest {

    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(repo, mock(RefreshTokenRepository.class), new BCryptPasswordEncoder(4),
                mock(AuditService.class), new TestSupport.RelogioFalso());
        TestSupport.logarComo(1L, 5L, Perfil.ADMIN);
        when(repo.save(any())).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            u.setId(99L);
            return u;
        });
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void criaNaAutoescolaDoAdmin() {
        var r = service.criar(new CriarUsuarioRequest("Sara", "SARA@x.com", "Senha123", Perfil.SECRETARIA));
        assertThat(r.email()).isEqualTo("sara@x.com");
        assertThat(r.perfil()).isEqualTo(Perfil.SECRETARIA);
    }

    @Test
    void naoCriaSuperAdminNemAluno() {
        assertThatThrownBy(() -> service.criar(new CriarUsuarioRequest("X", "x@x.com", "Senha123", Perfil.SUPER_ADMIN)))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.criar(new CriarUsuarioRequest("X", "x@x.com", "Senha123", Perfil.ALUNO)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void emailDuplicado() {
        when(repo.existsByEmail("x@x.com")).thenReturn(true);
        assertThatThrownBy(() -> service.criar(new CriarUsuarioRequest("X", "x@x.com", "Senha123", Perfil.INSTRUTOR)))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void naoEnxergaUsuarioDeOutraAutoescola() {
        when(repo.findByIdAndAutoescolaId(50L, 5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.buscar(50L)).isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void naoDesativaUltimoAdmin() {
        Usuario outroAdmin = usuario(2L, Perfil.ADMIN);
        when(repo.findByIdAndAutoescolaId(2L, 5L)).thenReturn(Optional.of(outroAdmin));
        when(repo.countByAutoescolaIdAndPerfilAndAtivoTrue(5L, Perfil.ADMIN)).thenReturn(1L);
        assertThatThrownBy(() -> service.atualizar(2L, new AtualizarUsuarioRequest("B", Perfil.ADMIN, false)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void naoAlteraProprioPerfil() {
        when(repo.findByIdAndAutoescolaId(1L, 5L)).thenReturn(Optional.of(usuario(1L, Perfil.ADMIN)));
        assertThatThrownBy(() -> service.atualizar(1L, new AtualizarUsuarioRequest("A", Perfil.SECRETARIA, true)))
                .isInstanceOf(RegraNegocioException.class);
    }

    private Usuario usuario(Long id, Perfil perfil) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setAutoescolaId(5L);
        u.setNome("U");
        u.setEmail("u" + id + "@x.com");
        u.setPerfil(perfil);
        u.setAtivo(true);
        return u;
    }
}
