package br.com.cfc.autoescola;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutoescolaRepository extends JpaRepository<Autoescola, Long> {

    boolean existsByCnpj(String cnpj);

    boolean existsByCnpjAndIdNot(String cnpj, Long id);

    @Query("""
            select a from Autoescola a
            where (:busca is null or lower(a.nome) like lower(concat('%', :busca, '%')) or a.cnpj like concat('%', :busca, '%'))
            order by a.nome
            """)
    Page<Autoescola> buscar(@Param("busca") String busca, Pageable pageable);
}
