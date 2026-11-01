package br.com.cfc.matricula;

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

/** Aulas práticas que o aluno tem direito de fazer: as do pacote e as compradas avulsas depois. */
@Getter
@Setter
@Entity
@Table(name = "credito_aula")
public class CreditoAula extends TenantEntity {

    @Column(name = "matricula_id", nullable = false)
    private Long matriculaId;

    @Column(name = "aluno_id", nullable = false)
    private Long alunoId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private OrigemCredito origem;

    @Column(nullable = false)
    private int quantidade;

    @Column(name = "valor_unitario", precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    @Column(length = 120)
    private String descricao;
}
