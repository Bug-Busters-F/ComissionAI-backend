package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.FechamentoCompetencia;
import com.bugbusters.backend.model.StatusFechamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FechamentoCompetenciaRepository extends JpaRepository<FechamentoCompetencia, UUID> {

    Optional<FechamentoCompetencia> findFirstByCompetenciaAndStatusOrderByConcluidoEmDesc(String competencia, StatusFechamento status);

    Optional<FechamentoCompetencia> findFirstByCompetenciaOrderByIniciadoEmDesc(String competencia);

    boolean existsByCompetenciaAndStatus(String competencia, StatusFechamento status);

    boolean existsByCompetenciaInAndStatus(java.util.Collection<String> competencias, StatusFechamento status);

    boolean existsByCompetenciaInAndStatusIn(java.util.Collection<String> competencias, java.util.Collection<StatusFechamento> statuses);

    Optional<FechamentoCompetencia> findByProtocolo(UUID protocolo);

    Page<FechamentoCompetencia> findAllByOrderByIniciadoEmDesc(Pageable pageable);

    Page<FechamentoCompetencia> findByCompetenciaOrderByIniciadoEmDesc(String competencia, Pageable pageable);

    List<FechamentoCompetencia> findAllByCompetencia(String competencia);
}
