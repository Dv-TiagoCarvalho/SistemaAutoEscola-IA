package br.com.cfc.relatorio;

import br.com.cfc.agenda.AgendaDtos.VeiculoResponse;
import br.com.cfc.agenda.AulaRepository;
import br.com.cfc.agenda.StatusAula;
import br.com.cfc.agenda.VeiculoService;
import br.com.cfc.aluno.AlunoRepository;
import br.com.cfc.autoescola.ConfiguracaoAutoescola;
import br.com.cfc.autoescola.ConfiguracaoService;
import br.com.cfc.exame.ExameRepository;
import br.com.cfc.exame.ResultadoExame;
import br.com.cfc.financeiro.FinanceiroDtos.ResumoFinanceiro;
import br.com.cfc.financeiro.FinanceiroService;
import br.com.cfc.shared.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Números da tela inicial da secretaria/administrador. */
@Service
public class DashboardService {

    public record AlertaVeiculo(Long id, String placa, List<String> alertas) {
    }

    public record DashboardResponse(long alunosAtivos, long aulasHoje, long aulasAgendadasSemana, long examesProximos7Dias,
                                    long titulosVencidos, BigDecimal valorVencido, BigDecimal recebidoNoMes,
                                    BigDecimal despesasPagasNoMes, BigDecimal saldoDoMes, List<AlertaVeiculo> veiculosComAlerta) {
    }

    private final AlunoRepository alunos;
    private final AulaRepository aulas;
    private final ExameRepository exames;
    private final FinanceiroService financeiro;
    private final VeiculoService veiculos;
    private final ConfiguracaoService configuracaoService;

    public DashboardService(AlunoRepository alunos, AulaRepository aulas, ExameRepository exames,
                            FinanceiroService financeiro, VeiculoService veiculos, ConfiguracaoService configuracaoService) {
        this.alunos = alunos;
        this.aulas = aulas;
        this.exames = exames;
        this.financeiro = financeiro;
        this.veiculos = veiculos;
        this.configuracaoService = configuracaoService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse obter() {
        SecurityUtils.autoescolaIdObrigatorio();
        ConfiguracaoAutoescola cfg = configuracaoService.configuracaoAtual();
        ZoneId zona = ZoneId.of(cfg.getFusoHorario());
        LocalDate hoje = configuracaoService.hoje();
        Instant inicioHoje = hoje.atStartOfDay(zona).toInstant();
        Instant fimHoje = hoje.plusDays(1).atStartOfDay(zona).toInstant();
        Instant fimSemana = hoje.plusDays(7).atStartOfDay(zona).toInstant();

        long aulasHoje = aulas.buscar(inicioHoje, fimHoje, null, null, null, StatusAula.AGENDADA).size();
        long aulasSemana = aulas.buscar(inicioHoje, fimSemana, null, null, null, StatusAula.AGENDADA).size();
        long examesProximos = exames.buscar(inicioHoje, fimSemana, null, ResultadoExame.AGENDADO, null).size();
        ResumoFinanceiro f = financeiro.resumo(null);
        List<AlertaVeiculo> alertas = veiculos.listar(false).stream()
                .filter(v -> !v.alertas().isEmpty())
                .map((VeiculoResponse v) -> new AlertaVeiculo(v.id(), v.placa(), v.alertas())).toList();

        return new DashboardResponse(alunos.countByAtivoTrue(), aulasHoje, aulasSemana, examesProximos, f.titulosVencidos(),
                f.vencido(), f.recebidoNoMes(), f.despesasPagasNoMes(), f.saldoDoMes(), alertas);
    }
}
