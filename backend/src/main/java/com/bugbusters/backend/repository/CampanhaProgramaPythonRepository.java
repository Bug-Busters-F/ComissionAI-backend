package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.CampanhaProgramaPython;
import com.bugbusters.backend.model.StatusAprovacaoPrograma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampanhaProgramaPythonRepository extends JpaRepository<CampanhaProgramaPython, UUID> {

    Optional<CampanhaProgramaPython> findFirstByCampanhaVersaoIdOrderByVersaoProgramaDesc(Long campanhaVersaoId);

    List<CampanhaProgramaPython> findAllByCampanhaVersaoIdOrderByVersaoProgramaDesc(Long campanhaVersaoId);

    Optional<CampanhaProgramaPython> findByCampanhaVersaoIdAndVersaoPrograma(Long campanhaVersaoId, Integer versaoPrograma);

    Optional<CampanhaProgramaPython> findByIdAndCampanhaVersaoId(UUID id, Long campanhaVersaoId);

    Optional<CampanhaProgramaPython> findFirstByCampanhaVersaoIdAndStatusAprovacao(Long campanhaVersaoId, StatusAprovacaoPrograma statusAprovacao);
}

