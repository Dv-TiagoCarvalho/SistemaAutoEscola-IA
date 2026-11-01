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
@Table(name = "presenca_teorica")
public class PresencaTeorica extends TenantEntity {

    @Column(name = "aula_teorica_id", nullable = false)
    private Long aulaTeoricaId;

    @Column(name = "aluno_id", nullable = false)
    private Long alunoId;

    @Column(nullable = false)
    private boolean presente;
}
