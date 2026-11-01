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
@Table(name = "simulado_questao")
public class SimuladoQuestao extends TenantEntity {

    @Column(name = "simulado_id", nullable = false)
    private Long simuladoId;

    @Column(name = "questao_id", nullable = false)
    private Long questaoId;

    @Column(nullable = false)
    private int ordem;

    @Column(length = 1)
    private String resposta;

    private Boolean correta;
}
