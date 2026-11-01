package br.com.cfc.agenda;

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

@Getter
@Setter
@Entity
@Table(name = "veiculo")
public class Veiculo extends TenantEntity {

    @Column(nullable = false, length = 7)
    private String placa;

    @Column(nullable = false, length = 80)
    private String modelo;

    @Column(nullable = false)
    private int ano;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 5)
    private Categoria categoria;

    @Column(name = "km_atual", nullable = false)
    private int kmAtual;

    @Column(name = "licenciamento_validade")
    private java.time.LocalDate licenciamentoValidade;

    @Column(name = "seguro_validade")
    private java.time.LocalDate seguroValidade;

    @Column(name = "proxima_revisao_km")
    private Integer proximaRevisaoKm;

    @Column(nullable = false)
    private boolean ativo = true;
}
