package br.com.cfc.aluno;

import br.com.cfc.TestSupport;
import br.com.cfc.aluno.AlunoDtos.ProcessoRequest;
import br.com.cfc.aluno.AlunoDtos.ProcessoUpdateRequest;
import br.com.cfc.autoescola.Categoria;
import br.com.cfc.autoescola.ConfiguracaoService;
import br.com.cfc.autoescola.RegraCategoria;
import br.com.cfc.autoescola.RegraCategoriaRepository;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.exception.ConflitoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.Perfil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessoServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 1);

    private final ProcessoRepository repo = mock(ProcessoRepository.class);
    private final AlunoService alunos = mock(AlunoService.class);
    private final RegraCategoriaRepository regras = mock(RegraCategoriaRepository.class);
    private final ConfiguracaoService config = mock(ConfiguracaoService.class);
    private ProcessoService service;
    private Aluno aluno;

    @BeforeEach
    void setUp() {
        service = new ProcessoService(repo, alunos, regras, config, mock(AuditService.class));
        TestSupport.logarComo(1L, 5L, Perfil.SECRETARIA);
        when(config.hoje()).thenReturn(HOJE);
        aluno = new Aluno();
        aluno.setId(30L);
        aluno.setAutoescolaId(5L);
        aluno.setNome("Maria");
        aluno.setAtivo(true);
        aluno.setDataNascimento(HOJE.minusYears(20));
        when(alunos.exigir(30L)).thenReturn(aluno);
        when(regras.findByCategoria(Categoria.B)).thenReturn(Optional.of(regra(Categoria.B, 18, true)));
        when(repo.save(any())).thenAnswer(i -> {
            ProcessoHabilitacao p = i.getArgument(0);
            p.setId(1L);
            return p;
        });
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private RegraCategoria regra(Categoria c, int idade, boolean ativa) {
        RegraCategoria r = new RegraCategoria();
        r.setCategoria(c);
        r.setIdadeMinima(idade);
        r.setAtiva(ativa);
        r.setHorasPraticasMinimas(new BigDecimal("2.0"));
        r.setHorasTeoricasMinimas(BigDecimal.ZERO);
        return r;
    }

    private ProcessoRequest reqB() {
        return new ProcessoRequest(Categoria.B, TipoProcesso.PRIMEIRA_HABILITACAO, "mg123456789", null);
    }

    @Test
    void criaProcessoNormalizandoRenach() {
        var r = service.criar(30L, reqB());
        assertThat(r.renach()).isEqualTo("MG123456789");
        assertThat(r.situacao()).isEqualTo(SituacaoProcesso.MATRICULADO);
        assertThat(r.alertas()).contains("Exame médico pendente.", "Exame psicológico pendente.");
    }

    @Test
    void respeitaIdadeMinimaDaCategoria() {
        aluno.setDataNascimento(HOJE.minusYears(17).minusDays(1));
        assertThatThrownBy(() -> service.criar(30L, reqB()))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("18");
        aluno.setDataNascimento(HOJE.minusYears(18)); // faz 18 hoje: pode
        assertThat(service.criar(30L, reqB()).id()).isEqualTo(1L);
    }

    @Test
    void categoriaInativaOuProcessoEmAndamento() {
        when(regras.findByCategoria(Categoria.B)).thenReturn(Optional.of(regra(Categoria.B, 18, false)));
        assertThatThrownBy(() -> service.criar(30L, reqB())).isInstanceOf(RegraNegocioException.class);

        when(regras.findByCategoria(Categoria.B)).thenReturn(Optional.of(regra(Categoria.B, 18, true)));
        when(repo.existsByAlunoIdAndCategoriaAndSituacaoNotIn(any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.criar(30L, reqB())).isInstanceOf(ConflitoException.class);
    }

    @Test
    void alertasDeLadv() {
        ProcessoHabilitacao p = new ProcessoHabilitacao();
        p.setExameMedico(StatusExame.APTO);
        p.setExamePsicologico(StatusExame.APTO);
        p.setSituacao(SituacaoProcesso.EM_AULAS);

        p.setLadvValidade(HOJE.minusDays(1));
        assertThat(AlunoDtos.ProcessoResponse.de(p, HOJE).alertas()).containsExactly("LADV vencida em 31/10/2026.");
        p.setLadvValidade(HOJE.plusDays(10));
        assertThat(AlunoDtos.ProcessoResponse.de(p, HOJE).alertas()).containsExactly("LADV vence em 10 dia(s).");
        p.setLadvValidade(HOJE.plusDays(200));
        assertThat(AlunoDtos.ProcessoResponse.de(p, HOJE).alertas()).isEmpty();
        p.setSituacao(SituacaoProcesso.APROVADO);
        p.setLadvValidade(HOJE.minusDays(5));
        assertThat(AlunoDtos.ProcessoResponse.de(p, HOJE).alertas()).isEmpty();
    }

    @Test
    void processoEncerradoNaoPodeSerAlterado() {
        ProcessoHabilitacao p = new ProcessoHabilitacao();
        p.setId(9L);
        p.setAutoescolaId(5L);
        p.setSituacao(SituacaoProcesso.APROVADO);
        when(repo.findById(9L)).thenReturn(Optional.of(p));
        var req = new ProcessoUpdateRequest(null, StatusExame.APTO, StatusExame.APTO, null, null, SituacaoProcesso.EM_AULAS, null);
        assertThatThrownBy(() -> service.atualizar(9L, req)).isInstanceOf(RegraNegocioException.class);
    }
}
