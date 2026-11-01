package br.com.cfc.agenda;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VeiculoRepository extends JpaRepository<Veiculo, Long> {

    @Query("select v from Veiculo v where (:incluirInativos = true or v.ativo = true) order by v.placa")
    List<Veiculo> listar(@Param("incluirInativos") boolean incluirInativos);

    boolean existsByPlaca(String placa);

    List<Veiculo> findByIdIn(Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Veiculo v where v.id = :id")
    Optional<Veiculo> buscarComLock(@Param("id") Long id);
}
