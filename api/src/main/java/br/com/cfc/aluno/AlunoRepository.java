package br.com.cfc.aluno;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtrado automaticamente pela autoescola do usuário logado (TenantEntity). */
public interface AlunoRepository extends JpaRepository<Aluno, Long> {

    boolean existsByCpf(String cpf);

    boolean existsByCpfAndIdNot(String cpf, Long id);

    @Query("""
            select a from Aluno a
            where (:incluirInativos = true or a.ativo = true)
              and (:busca is null
                   or lower(a.nome) like lower(concat('%', :busca, '%'))
                   or a.cpf like concat('%', :cpf, '%'))
              and (:situacao is null or exists (
                   select 1 from ProcessoHabilitacao p where p.alunoId = a.id and p.situacao = :situacao))
            order by a.nome
            """)
    Page<Aluno> buscar(@Param("busca") String busca,
                       @Param("cpf") String cpf,
                       @Param("situacao") SituacaoProcesso situacao,
                       @Param("incluirInativos") boolean incluirInativos,
                       Pageable pageable);
}
