package br.com.cfc.aluno;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsentimentoRepository extends JpaRepository<ConsentimentoLgpd, Long> {

    List<ConsentimentoLgpd> findByAlunoIdOrderByAceitoEmDescIdDesc(Long alunoId);

    boolean existsByAlunoIdAndVersaoTermoAndRevogadoEmIsNull(Long alunoId, String versaoTermo);
}
