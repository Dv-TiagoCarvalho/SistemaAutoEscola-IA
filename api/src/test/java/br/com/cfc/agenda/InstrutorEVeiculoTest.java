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

    private br.com.cfc.autoescola.ConfiguracaoService configVeiculo() {
        var c = mock(br.com.cfc.autoescola.ConfiguracaoService.class);
        when(c.hoje()).thenReturn(java.time.LocalDate.of(2026, 11, 1));
        return c;
    }

    @Test
    void alertasDeVeiculoPorValidadeERevisao() {
        Veiculo v = new Veiculo();
        v.setId(80L);
        v.setPlaca("ABC1D23");
        v.setModelo("Onix");
        v.setCategoria(Categoria.B);
        v.setKmAtual(19800);
        v.setProximaRevisaoKm(20000);
        v.setLicenciamentoValidade(java.time.LocalDate.of(2026, 10, 30));
        v.setSeguroValidade(java.time.LocalDate.of(2026, 11, 11));
        var r = AgendaDtos.VeiculoResponse.de(v, java.time.LocalDate.of(2026, 11, 1));
        assertThat(r.alertas()).containsExactly("Licenciamento vencido.", "Seguro vence em 10 dia(s).", "Revisão próxima (km).");
    }

    @Test
    void manutencaoAtualizaOKmDoVeiculo() {
        Veiculo v = new Veiculo();
        v.setId(80L);
        v.setAutoescolaId(5L);
        v.setKmAtual(1000);
        when(veiculoRepo.findById(80L)).thenReturn(java.util.Optional.of(v));
        var manut = mock(ManutencaoRepository.class);
        when(manut.save(any())).thenAnswer(i -> {
            ManutencaoVeiculo m = i.getArgument(0);
            m.setId(1L);
            return m;
        });
        var service = new VeiculoService(veiculoRepo, manut, configVeiculo(), audit);
        service.registrarManutencao(80L, new AgendaDtos.ManutencaoRequest(java.time.LocalDate.of(2026, 10, 20), "Troca de óleo", 1500, new java.math.BigDecimal("250.00")));
        assertThat(v.getKmAtual()).isEqualTo(1500);
        assertThatThrownBy(() -> service.registrarManutencao(80L, new AgendaDtos.ManutencaoRequest(java.time.LocalDate.of(2026, 12, 1), "x", null, java.math.BigDecimal.ZERO)))
                .isInstanceOf(RegraNegocioException.class);
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
        var service = new VeiculoService(veiculoRepo, mock(ManutencaoRepository.class), configVeiculo(), audit);
        var r = service.criar(new VeiculoRequest("abc-1d23", "Onix", 2022, Categoria.B, null, null, null, null));
        assertThat(r.placa()).isEqualTo("ABC1D23");

        when(veiculoRepo.existsByPlaca("ABC1D23")).thenReturn(true);
        assertThatThrownBy(() -> service.criar(new VeiculoRequest("ABC1D23", "Onix", 2022, Categoria.B, null, null, null, null)))
                .isInstanceOf(ConflitoException.class);
    }
}
