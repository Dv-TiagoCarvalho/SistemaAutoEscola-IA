package br.com.cfc.aluno;

import br.com.cfc.autoescola.Categoria;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProcessoRepository extends JpaRepository<ProcessoHabilitacao, Long> {

    List<ProcessoHabilitacao> findByAlunoIdOrderByCriadoEmDescIdDesc(Long alunoId);

    List<ProcessoHabilitacao> findByAlunoIdInOrderByCriadoEmDescIdDesc(Collection<Long> alunoIds);

    boolean existsByAlunoIdAndCategoriaAndSituacaoNotIn(Long alunoId, Categoria categoria, Collection<SituacaoProcesso> situacoes);

    /** Trava a linha até o fim da transação: evita duas matrículas ativas criadas ao mesmo tempo no mesmo processo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProcessoHabilitacao p where p.id = :id")
    Optional<ProcessoHabilitacao> buscarComLock(@Param("id") Long id);
}
