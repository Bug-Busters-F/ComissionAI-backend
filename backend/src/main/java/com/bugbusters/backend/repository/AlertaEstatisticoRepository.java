package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.AlertaEstatistico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.*;

public interface AlertaEstatisticoRepository extends JpaRepository<AlertaEstatistico, UUID>, JpaSpecificationExecutor<AlertaEstatistico> {
    List<AlertaEstatistico> findByLotePreparacaoIdAndSubstituidoEmIsNull(UUID loteId);
    boolean existsByLotePreparacaoIdAndAssinaturaAndSubstituidoEmIsNull(UUID loteId, String assinatura);
}
