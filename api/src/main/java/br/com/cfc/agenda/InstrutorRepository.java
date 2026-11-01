package br.com.cfc.agenda;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InstrutorRepository extends JpaRepository<Instrutor, Long> {

    @Query("select i from Instrutor i where (:incluirInativos = true or i.ativo = true) order by i.nome")
    List<Instrutor> listar(@Param("incluirInativos") boolean incluirInativos);

    Optional<Instrutor> findByUsuarioId(Long usuarioId);

    List<Instrutor> findByIdIn(Collection<Long> ids);

    /** Trava o instrutor: dois agendamentos ao mesmo tempo não passam juntos pela checagem de conflito. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Instrutor i where i.id = :id")
    Optional<Instrutor> buscarComLock(@Param("id") Long id);
}
