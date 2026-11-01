package br.com.cfc.usuario;

import br.com.cfc.shared.domain.BaseEntity;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.shared.security.UsuarioAutenticado;
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

/**
 * Usuário do sistema. Não usa @TenantId porque:
 * <ul>
 *   <li>o login procura o e-mail antes de saber a autoescola;</li>
 *   <li>o SUPER_ADMIN não pertence a nenhuma autoescola (autoescola_id nulo).</li>
 * </ul>
 * Por isso o {@link UsuarioService} SEMPRE filtra por autoescola_id manualmente.
 */
@Getter
@Setter
@Entity
@Table(name = "usuario")
public class Usuario extends BaseEntity {

    @Column(name = "autoescola_id")
    private Long autoescolaId;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Único no sistema inteiro, sempre em minúsculas. */
    @Column(nullable = false, length = 160, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Perfil perfil;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "ultimo_login")
    private Instant ultimoLogin;

    public UsuarioAutenticado comoAutenticado() {
        return new UsuarioAutenticado(getId(), autoescolaId, perfil, nome, email);
    }

    public static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
