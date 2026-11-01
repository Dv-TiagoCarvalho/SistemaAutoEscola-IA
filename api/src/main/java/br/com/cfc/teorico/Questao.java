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
@Table(name = "questao")
public class Questao extends TenantEntity {

    @Column(nullable = false, length = 60)
    private String tema;

    @Column(nullable = false, length = 1000)
    private String enunciado;

    @Column(name = "alt_a", nullable = false, length = 400)
    private String altA;

    @Column(name = "alt_b", nullable = false, length = 400)
    private String altB;

    @Column(name = "alt_c", nullable = false, length = 400)
    private String altC;

    @Column(name = "alt_d", nullable = false, length = 400)
    private String altD;

    /** Letra da alternativa correta: A, B, C ou D. */
    @Column(nullable = false, length = 1)
    private String correta;

    @Column(length = 600)
    private String explicacao;

    @Column(nullable = false)
    private boolean ativa = true;
}
