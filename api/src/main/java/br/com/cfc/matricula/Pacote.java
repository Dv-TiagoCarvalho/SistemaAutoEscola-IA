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

@Getter
@Setter
@Entity
@Table(name = "pacote")
public class Pacote extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 5)
    private Categoria categoria;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "qtd_aulas_praticas", nullable = false)
    private int qtdAulasPraticas;

    @Column(name = "teorico_incluso", nullable = false)
    private boolean teoricoIncluso = true;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false)
    private boolean ativo = true;
}
