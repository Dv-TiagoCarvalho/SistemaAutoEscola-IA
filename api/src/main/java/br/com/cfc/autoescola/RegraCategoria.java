package br.com.cfc.autoescola;

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

/**
 * Exigências legais por categoria (carga horária mínima, idade). Ficam em tabela porque
 * mudam com resoluções do Contran e podem variar entre estados.
 * Primeira entidade com {@code @TenantId}: o filtro por autoescola é automático.
 */
@Getter
@Setter
@Entity
@Table(name = "regra_categoria")
public class RegraCategoria extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 5)
    private Categoria categoria;

    @Column(name = "horas_praticas_minimas", nullable = false, precision = 5, scale = 1)
    private BigDecimal horasPraticasMinimas;

    @Column(name = "horas_teoricas_minimas", nullable = false, precision = 5, scale = 1)
    private BigDecimal horasTeoricasMinimas;

    @Column(name = "idade_minima", nullable = false)
    private int idadeMinima;

    @Column(nullable = false)
    private boolean ativa = true;
}
