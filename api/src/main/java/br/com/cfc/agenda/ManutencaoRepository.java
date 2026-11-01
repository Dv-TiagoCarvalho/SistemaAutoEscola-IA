package br.com.cfc.agenda;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManutencaoRepository extends JpaRepository<ManutencaoVeiculo, Long> {
    List<ManutencaoVeiculo> findByVeiculoIdOrderByDataDescIdDesc(Long veiculoId);
}
