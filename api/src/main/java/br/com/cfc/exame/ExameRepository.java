package br.com.cfc.exame;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ExameRepository extends JpaRepository<Exame, Long> {

    List<Exame> findByProcessoIdOrderByDataHoraDescIdDesc(Long processoId);

    long countByProcessoIdAndTipoAndResultadoIn(Long processoId, TipoExame tipo, Collection<ResultadoExame> resultados);

    boolean existsByProcessoIdAndTipoAndResultado(Long processoId, TipoExame tipo, ResultadoExame resultado);

    @Query("""
            select e from Exame e
            where e.dataHora >= :de and e.dataHora < :ate
              and (:tipo is null or e.tipo = :tipo)
              and (:resultado is null or e.resultado = :resultado)
              and (:alunoId is null or e.alunoId = :alunoId)
            order by e.dataHora, e.id
            """)
    List<Exame> buscar(@Param("de") Instant de, @Param("ate") Instant ate, @Param("tipo") TipoExame tipo,
                       @Param("resultado") ResultadoExame resultado, @Param("alunoId") Long alunoId);
}
