package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.LotePreparacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Sort;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LotePreparacaoRepository extends JpaRepository<LotePreparacao, UUID>, JpaSpecificationExecutor<LotePreparacao> {

    // Only bind filters that are present. Applying UPPER to an untyped null
    // parameter can make PostgreSQL infer bytea instead of text.
    default List<LotePreparacao> findByCampanhaIdAndFiltros(
            Long campanhaId, String tipoBase, String competencia, String status) {
        return findAll((root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            filters.add(cb.equal(root.get("campanha").get("id"), campanhaId));
            if (tipoBase != null && !tipoBase.isBlank()) filters.add(cb.equal(cb.upper(root.get("tipoBase")), tipoBase.trim().toUpperCase(Locale.ROOT)));
            if (competencia != null && !competencia.isBlank()) filters.add(cb.equal(root.get("competencia"), competencia.trim()));
            if (status != null && !status.isBlank()) filters.add(cb.equal(cb.upper(root.get("status")), status.trim().toUpperCase(Locale.ROOT)));
            return cb.and(filters.toArray(Predicate[]::new));
        }, Sort.by(Sort.Direction.DESC, "criadoEm"));
    }

    Optional<LotePreparacao> findByIdAndCampanhaId(UUID id, Long campanhaId);

    Optional<LotePreparacao> findFirstByEnvioArquivoEfetivadoIdOrderByEfetivadoEmDesc(Long envioArquivoEfetivadoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LotePreparacao l WHERE l.id = :id AND l.campanha.id = :campanhaId")
    Optional<LotePreparacao> findByIdAndCampanhaIdForUpdate(@Param("id") UUID id, @Param("campanhaId") Long campanhaId);

    List<LotePreparacao> findByCampanhaIdAndStatusOrderByCriadoEmDesc(Long campanhaId, String status);
}
