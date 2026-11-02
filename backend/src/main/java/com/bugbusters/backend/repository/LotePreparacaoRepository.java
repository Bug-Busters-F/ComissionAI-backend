package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.LotePreparacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LotePreparacaoRepository extends JpaRepository<LotePreparacao, UUID> {

    @Query("""
        SELECT l FROM LotePreparacao l
        WHERE l.campanha.id = :campanhaId
          AND (:tipoBase IS NULL OR UPPER(l.tipoBase) = UPPER(:tipoBase))
          AND (:competencia IS NULL OR l.competencia = :competencia)
          AND (:status IS NULL OR UPPER(l.status) = UPPER(:status))
        ORDER BY l.criadoEm DESC
    """)
    List<LotePreparacao> findByCampanhaIdAndFiltros(
            @Param("campanhaId") Long campanhaId,
            @Param("tipoBase") String tipoBase,
            @Param("competencia") String competencia,
            @Param("status") String status
    );

    Optional<LotePreparacao> findByIdAndCampanhaId(UUID id, Long campanhaId);

    List<LotePreparacao> findByCampanhaIdAndStatusOrderByCriadoEmDesc(Long campanhaId, String status);
}
