package br.com.cfc.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    List<Pagamento> findByTituloIdInAndEstornadoFalse(Collection<Long> tituloIds);

    @Query("""
            select coalesce(sum(p.valor), 0) from Pagamento p
            where p.estornado = false and p.dataPagamento between :de and :ate
            """)
    BigDecimal somarNoPeriodo(@Param("de") LocalDate de, @Param("ate") LocalDate ate);
}
