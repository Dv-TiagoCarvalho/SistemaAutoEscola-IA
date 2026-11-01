package br.com.cfc.teorico;

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

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "aula_teorica")
public class AulaTeorica extends TenantEntity {

    @Column(nullable = false, length = 120)
    private String tema;

    @Column(name = "instrutor_id")
    private Long instrutorId;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fim;

    @Column(length = 120)
    private String local;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private StatusAulaTeorica status = StatusAulaTeorica.AGENDADA;
}
