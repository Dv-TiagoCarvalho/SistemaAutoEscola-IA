package br.com.cfc.aluno;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoRepository extends JpaRepository<DocumentoAluno, Long> {

    List<DocumentoAluno> findByAlunoIdOrderByCriadoEmDescIdDesc(Long alunoId);

    long countByAlunoId(Long alunoId);
}
