package br.com.cfc.autoescola;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Filtrado automaticamente pela autoescola do usuário logado (ver TenantEntity). */
public interface RegraCategoriaRepository extends JpaRepository<RegraCategoria, Long> {

    List<RegraCategoria> findAllByOrderByCategoriaAsc();

    Optional<RegraCategoria> findByCategoria(Categoria categoria);
}
