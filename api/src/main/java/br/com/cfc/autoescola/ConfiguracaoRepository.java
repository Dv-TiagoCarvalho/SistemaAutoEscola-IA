package br.com.cfc.autoescola;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracaoRepository extends JpaRepository<ConfiguracaoAutoescola, Long> {

    /** Trava a configuração para numerar recibos sem repetir número. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from ConfiguracaoAutoescola c where c.autoescolaId = :id")
    java.util.Optional<ConfiguracaoAutoescola> buscarComLock(@org.springframework.data.repository.query.Param("id") Long id);

}
