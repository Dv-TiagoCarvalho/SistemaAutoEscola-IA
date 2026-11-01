package br.com.cfc.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DespesaRepository extends JpaRepository<Despesa, Long> {

    @Query("""
            select d from Despesa d
            where d.vencimento between :de and :ate and (:status is null or d.status = :status)
            order by d.vencimento, d.id
            """)
    List<Despesa> buscar(@Param("de") LocalDate de, @Param("ate") LocalDate ate, @Param("status") StatusDespesa status);

    @Query("""
            select coalesce(sum(d.valor), 0) from Despesa d
            where d.status = br.com.cfc.financeiro.StatusDespesa.PAGA and d.dataPagamento between :de and :ate
            """)
    BigDecimal somarPagasNoPeriodo(@Param("de") LocalDate de, @Param("ate") LocalDate ate);

    @Query("select coalesce(sum(d.valor), 0) from Despesa d where d.status = br.com.cfc.financeiro.StatusDespesa.ABERTA")
    BigDecimal somarAbertas();
}
