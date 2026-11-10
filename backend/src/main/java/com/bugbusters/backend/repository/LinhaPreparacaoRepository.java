package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.LinhaPreparacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LinhaPreparacaoRepository extends JpaRepository<LinhaPreparacao, UUID>, JpaSpecificationExecutor<LinhaPreparacao> {

    List<LinhaPreparacao> findByLotePreparacaoIdOrderByNumeroLinhaAsc(UUID lotePreparacaoId);

    Optional<LinhaPreparacao> findByLotePreparacaoIdAndNumeroLinha(UUID lotePreparacaoId, int numeroLinha);

    long countByLotePreparacaoId(UUID lotePreparacaoId);

    int countByLotePreparacaoIdAndIgnorarNaEfetivacaoTrue(UUID lotePreparacaoId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM LinhaPreparacao lp WHERE lp.lotePreparacao.id = :lotePreparacaoId")
    int deleteByLotePreparacaoId(@Param("lotePreparacaoId") UUID lotePreparacaoId);
}
