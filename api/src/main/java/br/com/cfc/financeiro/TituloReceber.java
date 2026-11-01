package br.com.cfc.financeiro;

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
import java.time.LocalDate;

/** Uma parcela a receber do aluno. */
@Getter
@Setter
@Entity
@Table(name = "titulo_receber")
public class TituloReceber extends TenantEntity {

    @Column(name = "aluno_id", nullable = false)
    private Long alunoId;

    @Column(name = "matricula_id")
    private Long matriculaId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 15)
    private OrigemTitulo origem;

    @Column(nullable = false, length = 120)
    private String descricao;

    @Column(name = "numero_parcela", nullable = false)
    private int numeroParcela;

    @Column(name = "total_parcelas", nullable = false)
    private int totalParcelas;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate vencimento;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private StatusTitulo status = StatusTitulo.ABERTO;
}
