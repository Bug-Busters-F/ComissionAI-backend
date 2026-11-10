package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.HistoricoEdicaoPreparacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HistoricoEdicaoPreparacaoRepository extends JpaRepository<HistoricoEdicaoPreparacao, UUID> {
    List<HistoricoEdicaoPreparacao> findByLinhaPreparacaoIdOrderByEditadoEmDesc(UUID linhaPreparacaoId);

    List<HistoricoEdicaoPreparacao> findByLotePreparacaoIdOrderByEditadoEmDesc(UUID lotePreparacaoId);
}
