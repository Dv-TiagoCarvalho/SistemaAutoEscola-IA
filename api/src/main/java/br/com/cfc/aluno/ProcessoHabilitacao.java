package br.com.cfc.aluno;

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

import java.time.LocalDate;

/** Um processo de habilitação do aluno (um aluno pode ter vários: primeira CNH, adição, mudança de categoria). */
@Getter
@Setter
@Entity
@Table(name = "processo_habilitacao")
public class ProcessoHabilitacao extends TenantEntity {

    @Column(name = "aluno_id", nullable = false)
    private Long alunoId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 5)
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private TipoProcesso tipo;

    @Column(length = 20)
    private String renach;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "exame_medico", nullable = false, length = 10)
    private StatusExame exameMedico = StatusExame.PENDENTE;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "exame_psicologico", nullable = false, length = 10)
    private StatusExame examePsicologico = StatusExame.PENDENTE;

    @Column(name = "ladv_numero", length = 30)
    private String ladvNumero;

    @Column(name = "ladv_validade")
    private LocalDate ladvValidade;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SituacaoProcesso situacao = SituacaoProcesso.MATRICULADO;

    @Column(length = 500)
    private String observacoes;
}
