package br.com.cfc.teorico;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PresencaRepository extends JpaRepository<PresencaTeorica, Long> {
    List<PresencaTeorica> findByAulaTeoricaId(Long aulaTeoricaId);
}
