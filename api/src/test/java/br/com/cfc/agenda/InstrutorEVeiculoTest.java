package br.com.cfc.agenda;

import br.com.cfc.TestSupport;
import br.com.cfc.agenda.AgendaDtos.InstrutorRequest;
import br.com.cfc.agenda.AgendaDtos.VeiculoRequest;
import br.com.cfc.autoescola.Categoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.usuario.Usuario;
import br.com.cfc.usuario.UsuarioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstrutorEVeiculoTest {

    private final InstrutorRepository instrutorRepo = mock(InstrutorRepository.class);
    private final VeiculoRepository veiculoRepo = mock(VeiculoRepository.class);
    private final UsuarioService usuarios = mock(UsuarioService.class);
    private final AuditService audit = mock(AuditService.class);

    @BeforeEach
    void setUp() {
        TestSupport.logarComo(1L, 5L, Perfil.ADMIN);
        when(instrutorRepo.save(any())).thenAnswer(i -> {
            Instrutor x = i.getArgument(0);
            x.setId(70L);
            return x;
        });
        when(veiculoRepo.save(any())).thenAnswer(i -> {
            Veiculo x = i.getArgument(0);
            x.setId(80L);
            return x;
        });
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void instrutorComAcessoCriaUsuarioComPerfilInstrutor() {
        Usuario u = new Usuario();
        u.setId(88L);
        when(usuarios.novoUsuario(eq(5L), eq("Carlos"), eq("carlos@x.com"), eq("Senha@123"), eq(Perfil.INSTRUTOR))).thenReturn(u);
        var service = new InstrutorService(instrutorRepo, usuarios, audit);

        var r = service.criar(new InstrutorRequest(" Carlos ", "(31) 99999-0000", null, Set.of(Categoria.B, Categoria.A), "carlos@x.com", "Senha@123"));

        assertThat(r.temAcesso()).isTrue();
        assertThat(r.telefone()).isEqualTo("31999990000");
        assertThat(r.categorias()).containsExactly(Categoria.A, Categoria.B);
    }

    @Test
    void emailSemSenhaEhRejeitado() {
        var service = new InstrutorService(instrutorRepo, usuarios, audit);
        assertThatThrownBy(() -> service.criar(new InstrutorRequest("Carlos", null, null, Set.of(Categoria.B), "carlos@x.com", null)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void inativarInstrutorDesativaOAcesso() {
        Instrutor i = new Instrutor();
        i.setId(70L);
        i.setAutoescolaId(5L);
        i.setNome("Carlos");
        i.setUsuarioId(88L);
        i.definirCategorias(Set.of(Categoria.B));
        when(instrutorRepo.findById(70L)).thenReturn(java.util.Optional.of(i));
        new InstrutorService(instrutorRepo, usuarios, audit).alterarStatus(70L, false);
        assertThat(i.isAtivo()).isFalse();
        verify(usuarios).definirAtivoDeInstrutor(88L, false);
    }

    @Test
    void placaEhNormalizadaEUnica() {
        var service = new VeiculoService(veiculoRepo, audit);
        var r = service.criar(new VeiculoRequest("abc-1d23", "Onix", 2022, Categoria.B));
        assertThat(r.placa()).isEqualTo("ABC1D23");

        when(veiculoRepo.existsByPlaca("ABC1D23")).thenReturn(true);
        assertThatThrownBy(() -> service.criar(new VeiculoRequest("ABC1D23", "Onix", 2022, Categoria.B)))
                .isInstanceOf(ConflitoException.class);
    }
}
