package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.EstadoCampanha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampanhaRepository extends JpaRepository<Campanha, Long> {
    List<Campanha> findAllByRemovidoEmIsNullOrderByCriadoEmDesc();
    List<Campanha> findAllByEstadoAndRemovidoEmIsNullOrderByCriadoEmDesc(EstadoCampanha estado);
    Optional<Campanha> findByIdAndRemovidoEmIsNull(Long id);
}