package br.com.cfc.agenda;

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
@Table(name = "aula")
public class Aula extends TenantEntity {

    @Column(name = "aluno_id", nullable = false)
    private Long alunoId;

    @Column(name = "matricula_id", nullable = false)
    private Long matriculaId;

    @Column(name = "instrutor_id", nullable = false)
    private Long instrutorId;

    @Column(name = "veiculo_id", nullable = false)
    private Long veiculoId;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fim;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 15)
    private StatusAula status = StatusAula.AGENDADA;

    @Column(name = "motivo_cancelamento", length = 300)
    private String motivoCancelamento;

    @Column(name = "cancelamento_tardio", nullable = false)
    private boolean cancelamentoTardio;

    @Column(name = "km_inicial")
    private Integer kmInicial;

    @Column(name = "km_final")
    private Integer kmFinal;

    @Column(length = 300)
    private String conteudo;

    @Column(length = 500)
    private String observacoes;

    private Integer nota;

    @Column(name = "registrada_em")
    private Instant registradaEm;

    @Column(name = "registrada_por")
    private Long registradaPor;
}
