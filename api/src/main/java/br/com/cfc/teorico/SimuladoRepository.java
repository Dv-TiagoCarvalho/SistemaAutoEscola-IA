package br.com.cfc.teorico;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface SimuladoRepository extends JpaRepository<Simulado, Long> {

    List<Simulado> findTop50ByAlunoIdOrderByIniciadoEmDescIdDesc(Long alunoId);

    boolean existsByAlunoIdAndFinalizadoEmIsNull(Long alunoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Simulado s where s.id = :id")
    Optional<Simulado> buscarComLock(@Param("id") Long id);

    long countByAlunoIdAndFinalizadoEmIsNotNull(Long alunoId);

    long countByAlunoIdAndAprovadoTrue(Long alunoId);
}
