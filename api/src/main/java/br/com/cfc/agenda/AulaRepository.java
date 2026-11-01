package br.com.cfc.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AulaRepository extends JpaRepository<Aula, Long> {

    @Query("""
            select count(a) > 0 from Aula a
            where a.instrutorId = :id and a.status in :status and a.inicio < :fim and a.fim > :inicio
            """)
    boolean conflitoInstrutor(@Param("id") Long id, @Param("inicio") Instant inicio, @Param("fim") Instant fim,
                              @Param("status") Collection<StatusAula> status);

    @Query("""
            select count(a) > 0 from Aula a
            where a.veiculoId = :id and a.status in :status and a.inicio < :fim and a.fim > :inicio
            """)
    boolean conflitoVeiculo(@Param("id") Long id, @Param("inicio") Instant inicio, @Param("fim") Instant fim,
                            @Param("status") Collection<StatusAula> status);

    @Query("""
            select count(a) > 0 from Aula a
            where a.alunoId = :id and a.status in :status and a.inicio < :fim and a.fim > :inicio
            """)
    boolean conflitoAluno(@Param("id") Long id, @Param("inicio") Instant inicio, @Param("fim") Instant fim,
                          @Param("status") Collection<StatusAula> status);

    @Query("""
            select count(a) from Aula a
            where a.alunoId = :id and a.status in :status and a.inicio >= :de and a.inicio < :ate
            """)
    long contarDoAlunoNoPeriodo(@Param("id") Long id, @Param("de") Instant de, @Param("ate") Instant ate,
                                @Param("status") Collection<StatusAula> status);

    @Query("select count(a) from Aula a where a.matriculaId = :matriculaId and a.status in :status")
    long contarDaMatricula(@Param("matriculaId") Long matriculaId, @Param("status") Collection<StatusAula> status);

    @Query("""
            select a from Aula a
            where a.inicio >= :de and a.inicio < :ate
              and (:instrutorId is null or a.instrutorId = :instrutorId)
              and (:alunoId is null or a.alunoId = :alunoId)
              and (:veiculoId is null or a.veiculoId = :veiculoId)
              and (:status is null or a.status = :status)
            order by a.inicio, a.id
            """)
    List<Aula> buscar(@Param("de") Instant de, @Param("ate") Instant ate, @Param("instrutorId") Long instrutorId,
                      @Param("alunoId") Long alunoId, @Param("veiculoId") Long veiculoId,
                      @Param("status") StatusAula status);
}
