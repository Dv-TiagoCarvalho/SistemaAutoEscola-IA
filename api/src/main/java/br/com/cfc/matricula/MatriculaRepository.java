package br.com.cfc.matricula;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    boolean existsByProcessoIdAndStatus(Long processoId, StatusMatricula status);

    @Query("""
            select m from Matricula m
            where (:alunoId is null or m.alunoId = :alunoId)
              and (:status is null or m.status = :status)
            order by m.dataMatricula desc, m.id desc
            """)
    Page<Matricula> buscar(@Param("alunoId") Long alunoId, @Param("status") StatusMatricula status, Pageable pageable);
}
