package br.com.cfc.matricula;

import br.com.cfc.autoescola.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PacoteRepository extends JpaRepository<Pacote, Long> {

    @Query("""
            select p from Pacote p
            where (:categoria is null or p.categoria = :categoria)
              and (:incluirInativos = true or p.ativo = true)
            order by p.categoria, p.nome
            """)
    List<Pacote> buscar(@Param("categoria") Categoria categoria, @Param("incluirInativos") boolean incluirInativos);
}
