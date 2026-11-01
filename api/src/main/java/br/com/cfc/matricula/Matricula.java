package br.com.cfc.matricula;

import br.com.cfc.autoescola.Categoria;
import br.com.cfc.shared.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Matrícula do aluno em um pacote. Guarda uma cópia dos dados do pacote (nome, aulas, valor),
 * para que alterar o pacote depois não mude o que foi contratado.
 */
@Getter
@Setter
@Entity
@Table(name = "matricula")
public class Matricula extends TenantEntity {

    @Column(name = "processo_id", nullable = false)
    private Long processoId;

    @Column(name = "aluno_id", nullable = false)
    private Long alunoId;

    @Column(name = "pacote_id", nullable = false)
    private Long pacoteId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 5)
    private Categoria categoria;

    @Column(name = "pacote_nome", nullable = false, length = 100)
    private String pacoteNome;

    @Column(name = "aulas_praticas", nullable = false)
    private int aulasPraticas;

    @Column(name = "teorico_incluso", nullable = false)
    private boolean teoricoIncluso;

    @Column(name = "data_matricula", nullable = false)
    private LocalDate dataMatricula;

    @Column(name = "valor_bruto", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorBruto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 15)
    private StatusMatricula status = StatusMatricula.ATIVA;

    @Column(name = "motivo_cancelamento", length = 300)
    private String motivoCancelamento;

    @Column(name = "cancelada_em")
    private Instant canceladaEm;

    @Column(name = "contrato_path", length = 300)
    private String contratoPath;
}
