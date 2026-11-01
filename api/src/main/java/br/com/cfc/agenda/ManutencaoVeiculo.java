package br.com.cfc.agenda;

import br.com.cfc.shared.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "manutencao_veiculo")
public class ManutencaoVeiculo extends TenantEntity {

    @Column(name = "veiculo_id", nullable = false)
    private Long veiculoId;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false, length = 200)
    private String descricao;

    private Integer km;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor = BigDecimal.ZERO;
}
