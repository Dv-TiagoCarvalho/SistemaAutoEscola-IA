package br.com.cfc.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TituloReceberRepository extends JpaRepository<TituloReceber, Long> {

    List<TituloReceber> findByMatriculaIdOrderByVencimentoAscIdAsc(Long matriculaId);

    List<TituloReceber> findByMatriculaIdAndStatus(Long matriculaId, StatusTitulo status);
}
