package br.com.cfc.financeiro;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TituloReceberRepository extends JpaRepository<TituloReceber, Long> {

    List<TituloReceber> findByMatriculaIdOrderByVencimentoAscIdAsc(Long matriculaId);

    List<TituloReceber> findByMatriculaIdAndStatus(Long matriculaId, StatusTitulo status);

    List<TituloReceber> findByAlunoIdOrderByVencimentoAscIdAsc(Long alunoId);

    /** Trava o título: duas baixas ao mesmo tempo não geram dois recibos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TituloReceber t where t.id = :id")
    Optional<TituloReceber> buscarComLock(@Param("id") Long id);

    @Query("""
            select t from TituloReceber t
            where (:alunoId is null or t.alunoId = :alunoId)
              and (:status is null or t.status = :status)
              and (:de is null or t.vencimento >= :de)
              and (:ate is null or t.vencimento <= :ate)
            order by t.vencimento, t.id
            """)
    List<TituloReceber> buscar(@Param("alunoId") Long alunoId, @Param("status") StatusTitulo status,
                               @Param("de") LocalDate de, @Param("ate") LocalDate ate, Pageable pageable);

    @Query("""
            select coalesce(sum(t.valor), 0) from TituloReceber t
            where t.status = br.com.cfc.financeiro.StatusTitulo.ABERTO and (:ate is null or t.vencimento <= :ate)
            """)
    BigDecimal somarAbertosAte(@Param("ate") LocalDate ate);

    @Query("""
            select count(t) from TituloReceber t
            where t.status = br.com.cfc.financeiro.StatusTitulo.ABERTO and t.vencimento < :hoje
            """)
    long contarVencidos(@Param("hoje") LocalDate hoje);
}
