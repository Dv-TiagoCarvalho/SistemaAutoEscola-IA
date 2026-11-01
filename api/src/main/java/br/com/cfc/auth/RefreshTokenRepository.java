package br.com.cfc.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revogadoEm = :agora where t.usuarioId = :usuarioId and t.revogadoEm is null")
    int revogarTodosDoUsuario(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);

    @Modifying
    @Query("delete from RefreshToken t where t.expiraEm < :limite")
    int apagarExpiradosAntesDe(@Param("limite") Instant limite);
}
