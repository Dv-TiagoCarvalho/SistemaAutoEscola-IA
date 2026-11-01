package br.com.cfc.shared.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:autoescolaId is null or a.autoescolaId = :autoescolaId)
              and (:entidade is null or a.entidade = :entidade)
              and (:entidadeId is null or a.entidadeId = :entidadeId)
            order by a.dataHora desc, a.id desc
            """)
    Page<AuditLog> buscar(@Param("autoescolaId") Long autoescolaId,
                          @Param("entidade") String entidade,
                          @Param("entidadeId") Long entidadeId,
                          Pageable pageable);
}
