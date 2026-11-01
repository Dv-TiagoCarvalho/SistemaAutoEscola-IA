package br.com.cfc.aluno;

import br.com.cfc.shared.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Registro do aceite do termo de tratamento de dados (LGPD), com data, versão do termo e IP. */
@Getter
@Setter
@Entity
@Table(name = "consentimento_lgpd")
public class ConsentimentoLgpd extends TenantEntity {

    @Column(name = "aluno_id", nullable = false)
    private Long alunoId;

    @Column(name = "versao_termo", nullable = false, length = 20)
    private String versaoTermo;

    @Column(name = "aceito_em", nullable = false)
    private Instant aceitoEm;

    @Column(length = 45)
    private String ip;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    public boolean isVigente() {
        return revogadoEm == null;
    }
}
