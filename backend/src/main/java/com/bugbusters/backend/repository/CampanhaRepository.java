package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.EstadoCampanha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampanhaRepository extends JpaRepository<Campanha, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Campanha c where c.id = :id and c.removidoEm is null")
    Optional<Campanha> buscarParaCalibracao(@org.springframework.data.repository.query.Param("id") Long id);

    List<Campanha> findAllByRemovidoEmIsNullOrderByCriadoEmDesc();
    List<Campanha> findAllByEstadoAndRemovidoEmIsNullOrderByCriadoEmDesc(EstadoCampanha estado);
    Optional<Campanha> findByIdAndRemovidoEmIsNull(Long id);
    boolean existsByIdAndRemovidoEmIsNull(Long id);
}