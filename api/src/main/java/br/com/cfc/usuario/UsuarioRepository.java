package br.com.cfc.usuario;

import br.com.cfc.shared.security.Perfil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPerfil(Perfil perfil);

    Optional<Usuario> findByIdAndAutoescolaId(Long id, Long autoescolaId);

    @Query("""
            select u from Usuario u
            where u.autoescolaId = :autoescolaId
              and u.perfil in :perfis
              and (:busca is null or lower(u.nome) like lower(concat('%', :busca, '%'))
                   or u.email like lower(concat('%', :busca, '%')))
            order by u.nome
            """)
    Page<Usuario> buscar(@Param("autoescolaId") Long autoescolaId,
                         @Param("perfis") Collection<Perfil> perfis,
                         @Param("busca") String busca,
                         Pageable pageable);

    long countByAutoescolaIdAndPerfilAndAtivoTrue(Long autoescolaId, Perfil perfil);
}
