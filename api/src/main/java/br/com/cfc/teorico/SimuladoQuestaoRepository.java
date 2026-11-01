package br.com.cfc.teorico;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimuladoQuestaoRepository extends JpaRepository<SimuladoQuestao, Long> {
    List<SimuladoQuestao> findBySimuladoIdOrderByOrdem(Long simuladoId);
}
