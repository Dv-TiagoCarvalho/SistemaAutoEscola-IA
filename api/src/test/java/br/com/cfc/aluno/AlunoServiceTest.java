package br.com.cfc.aluno;

import br.com.cfc.TestSupport;
import br.com.cfc.aluno.AlunoDtos.AcessoRequest;
import br.com.cfc.aluno.AlunoDtos.AlunoRequest;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.exception.NaoEncontradoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.usuario.Usuario;
import br.com.cfc.usuario.UsuarioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlunoServiceTest {

    private final AlunoRepository repo = mock(AlunoRepository.class);
    private final UsuarioService usuarios = mock(UsuarioService.class);
    private AlunoService service;

    @BeforeEach
    void setUp() {
        service = new AlunoService(repo, mock(ProcessoRepository.class), usuarios, mock(AuditService.class));
        TestSupport.logarComo(1L, 5L, Perfil.SECRETARIA);
        when(repo.save(any())).thenAnswer(i -> {
            Aluno a = i.getArgument(0);
            a.setId(30L);
            return a;
        });
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private AlunoRequest req(String cpf, String telefone) {
        return new AlunoRequest("  Maria Silva ", cpf, null, LocalDate.of(2000, 5, 10), telefone, "MARIA@X.COM",
                "", null, null, null, null, null, "");
    }

    private Aluno aluno(Long id, Long autoescolaId) {
        Aluno a = new Aluno();
        a.setId(id);
        a.setAutoescolaId(autoescolaId);
        a.setNome("Maria");
        a.setCpf("52998224725");
        a.setAtivo(true);
        return a;
    }

    @Test
    void normalizaCpfTelefoneEEmail() {
        var r = service.criar(req("529.982.247-25", "(31) 99999-0000"));
        assertThat(r.cpf()).isEqualTo("52998224725");
        assertThat(r.telefone()).isEqualTo("31999990000");
        assertThat(r.email()).isEqualTo("maria@x.com");
        assertThat(r.nome()).isEqualTo("Maria Silva");
        assertThat(r.cep()).isNull();
        assertThat(r.uf()).isNull();
    }

    @Test
    void cpfDuplicadoNaMesmaAutoescola() {
        when(repo.existsByCpf("52998224725")).thenReturn(true);
        assertThatThrownBy(() -> service.criar(req("529.982.247-25", "31999990000"))).isInstanceOf(ConflitoException.class);
    }

    @Test
    void telefoneInvalido() {
        assertThatThrownBy(() -> service.criar(req("529.982.247-25", "9999"))).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void naoEnxergaAlunoDeOutraAutoescola() {
        when(repo.findById(40L)).thenReturn(Optional.of(aluno(40L, 99L)));
        assertThatThrownBy(() -> service.buscar(40L)).isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void criaAcessoDoAlunoComoUsuarioAluno() {
        Aluno a = aluno(30L, 5L);
        when(repo.findById(30L)).thenReturn(Optional.of(a));
        Usuario u = new Usuario();
        u.setId(77L);
        when(usuarios.novoUsuario(eq(5L), eq("Maria"), eq("maria@x.com"), eq("Senha123"), eq(Perfil.ALUNO))).thenReturn(u);

        var r = service.criarAcesso(30L, new AcessoRequest("maria@x.com", "Senha123"));

        assertThat(r.temAcesso()).isTrue();
        assertThat(a.getUsuarioId()).isEqualTo(77L);
        assertThatThrownBy(() -> service.criarAcesso(30L, new AcessoRequest("outro@x.com", "Senha123")))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void inativarAlunoDesativaOUsuarioDoApp() {
        Aluno a = aluno(30L, 5L);
        a.setUsuarioId(77L);
        when(repo.findById(30L)).thenReturn(Optional.of(a));
        service.alterarStatus(30L, false);
        assertThat(a.isAtivo()).isFalse();
        verify(usuarios).definirAtivoDeAluno(77L, false);
    }
}
