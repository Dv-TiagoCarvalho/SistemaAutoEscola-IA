package br.com.cfc.teorico;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface QuestaoRepository extends JpaRepository<Questao, Long> {

    @Query("""
            select q from Questao q
            where (:ativa is null or q.ativa = :ativa) and (:tema is null or q.tema = :tema)
            order by q.tema, q.id
            """)
    List<Questao> buscar(@Param("ativa") Boolean ativa, @Param("tema") String tema);

    List<Questao> findByAtivaTrue();

    List<Questao> findByIdIn(Collection<Long> ids);

    long countByAtivaTrue();
}
