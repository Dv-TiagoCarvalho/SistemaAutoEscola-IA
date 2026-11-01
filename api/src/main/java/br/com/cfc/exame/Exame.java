package br.com.cfc.exame;

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

/** Exame teórico ou prático (feito no órgão de trânsito; aqui só se registra data e resultado, sem integração). */
@Getter
@Setter
@Entity
@Table(name = "exame")
public class Exame extends TenantEntity {

    @Column(name = "aluno_id", nullable = false)
    private Long alunoId;

    @Column(name = "processo_id", nullable = false)
    private Long processoId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private TipoExame tipo;

    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;

    @Column(length = 120)
    private String local;

    @Column(nullable = false)
    private int tentativa = 1;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private ResultadoExame resultado = ResultadoExame.AGENDADO;

    @Column(length = 300)
    private String observacoes;
}
