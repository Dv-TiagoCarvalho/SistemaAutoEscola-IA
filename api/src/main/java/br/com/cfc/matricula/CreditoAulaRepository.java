package br.com.cfc.matricula;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditoAulaRepository extends JpaRepository<CreditoAula, Long> {

    List<CreditoAula> findByMatriculaIdOrderByIdAsc(Long matriculaId);
}
