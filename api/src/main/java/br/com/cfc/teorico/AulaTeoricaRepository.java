package br.com.cfc.teorico;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AulaTeoricaRepository extends JpaRepository<AulaTeorica, Long> {

    @Query("select a from AulaTeorica a where a.inicio >= :de and a.inicio < :ate order by a.inicio")
    List<AulaTeorica> buscar(@Param("de") Instant de, @Param("ate") Instant ate);

    /** Pares (inicio, fim) das aulas teóricas realizadas em que o aluno esteve presente. */
    @Query("""
            select a.inicio, a.fim from PresencaTeorica p, AulaTeorica a
            where p.aulaTeoricaId = a.id and p.alunoId = :alunoId and p.presente = true
              and a.status = br.com.cfc.teorico.StatusAulaTeorica.REALIZADA
            """)
    List<Object[]> periodosDePresenca(@Param("alunoId") Long alunoId);
}
