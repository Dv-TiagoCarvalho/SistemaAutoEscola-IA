package br.com.cfc.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Refresh token opaco. No banco fica só o hash SHA-256: um vazamento do banco não permite usar os tokens.
 * A cada uso o token é revogado e trocado por outro (rotação). Reuso de um token revogado
 * indica roubo, e todos os tokens do usuário são revogados.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    public RefreshToken(Long usuarioId, String tokenHash, Instant criadoEm, Instant expiraEm) {
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    public boolean isRevogado() {
        return revogadoEm != null;
    }

    public boolean isExpirado(Instant agora) {
        return !agora.isBefore(expiraEm);
    }
}
