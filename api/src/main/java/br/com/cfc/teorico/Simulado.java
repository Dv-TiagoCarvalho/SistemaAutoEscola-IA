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
@Table(name = "simulado")
public class Simulado extends TenantEntity {

    @Column(name = "aluno_id", nullable = false)
    private Long alunoId;

    @Column(name = "iniciado_em", nullable = false)
    private Instant iniciadoEm;

    @Column(name = "limite_em", nullable = false)
    private Instant limiteEm;

    @Column(name = "finalizado_em")
    private Instant finalizadoEm;

    @Column(name = "qtd_questoes", nullable = false)
    private int qtdQuestoes;

    /** Cópia da configuração no momento da prova: mudar a configuração depois não altera simulados antigos. */
    @Column(name = "acertos_minimos", nullable = false)
    private int acertosMinimos;

    private Integer acertos;

    private Boolean aprovado;

    @Column(nullable = false)
    private boolean expirado;
}
