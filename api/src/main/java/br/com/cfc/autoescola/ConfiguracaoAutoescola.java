package br.com.cfc.autoescola;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Regras operacionais de cada autoescola. Nada disso fica fixo no código porque varia por autoescola e por estado.
 * Chave primária = id da autoescola (relação 1:1).
 */
@Getter
@Setter
@Entity
@Table(name = "configuracao_autoescola")
public class ConfiguracaoAutoescola {

    @Id
    @Column(name = "autoescola_id")
    private Long autoescolaId;

    @Column(name = "duracao_aula_min", nullable = false)
    private int duracaoAulaMin = 50;

    @Column(name = "max_aulas_dia_aluno", nullable = false)
    private int maxAulasDiaAluno = 2;

    @Column(name = "antecedencia_cancelamento_horas", nullable = false)
    private int antecedenciaCancelamentoHoras = 24;

    @Column(name = "antecedencia_agendamento_horas", nullable = false)
    private int antecedenciaAgendamentoHoras = 2;

    @Column(name = "cobrar_falta", nullable = false)
    private boolean cobrarFalta = false;

    @Column(name = "valor_falta", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorFalta = BigDecimal.ZERO;

    @Column(name = "lembrete_horas_antes", nullable = false)
    private int lembreteHorasAntes = 24;

    @Column(name = "simulado_qtd_questoes", nullable = false)
    private int simuladoQtdQuestoes = 30;

    @Column(name = "simulado_tempo_min", nullable = false)
    private int simuladoTempoMin = 40;

    @Column(name = "simulado_acertos_minimos", nullable = false)
    private int simuladoAcertosMinimos = 21;

    /** Se ligado, só agenda aula prática para aluno com exames médico e psicológico APTOS. */
    @Column(name = "exigir_exames_aulas", nullable = false)
    private boolean exigirExamesParaAulas = true;

    @Column(name = "proximo_recibo", nullable = false)
    private long proximoRecibo = 1;

    @Column(name = "fuso_horario", nullable = false, length = 40)
    private String fusoHorario = "America/Sao_Paulo";

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Version
    @Column(nullable = false)
    private Long version;
}
