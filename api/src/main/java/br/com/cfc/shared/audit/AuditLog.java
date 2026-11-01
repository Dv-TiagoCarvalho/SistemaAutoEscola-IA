package br.com.cfc.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Registro imutável de "quem alterou o quê e quando".
 * Não usa @TenantId porque também registra ações do SUPER_ADMIN (sem autoescola);
 * as consultas filtram autoescola_id explicitamente.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "autoescola_id")
    private Long autoescolaId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "usuario_nome", length = 120)
    private String usuarioNome;

    @Column(nullable = false, length = 60)
    private String entidade;

    @Column(name = "entidade_id")
    private Long entidadeId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AcaoAuditoria acao;

    @Column(name = "dados_antes", columnDefinition = "json")
    private String dadosAntes;

    @Column(name = "dados_depois", columnDefinition = "json")
    private String dadosDepois;

    @Column(length = 45)
    private String ip;

    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;

    public AuditLog(Long autoescolaId, Long usuarioId, String usuarioNome, String entidade, Long entidadeId,
                    AcaoAuditoria acao, String dadosAntes, String dadosDepois, String ip, Instant dataHora) {
        this.autoescolaId = autoescolaId;
        this.usuarioId = usuarioId;
        this.usuarioNome = usuarioNome;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.acao = acao;
        this.dadosAntes = dadosAntes;
        this.dadosDepois = dadosDepois;
        this.ip = ip;
        this.dataHora = dataHora;
    }
}
