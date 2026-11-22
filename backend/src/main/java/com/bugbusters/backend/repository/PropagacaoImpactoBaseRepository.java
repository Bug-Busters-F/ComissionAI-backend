package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.PropagacaoImpactoBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PropagacaoImpactoBaseRepository extends JpaRepository<PropagacaoImpactoBase, UUID> {
    List<PropagacaoImpactoBase> findByLotePreparacaoId(UUID lotePreparacaoId);
    List<PropagacaoImpactoBase> findByCampanhaOrigemIdOrderByConfirmadoEmDesc(Long campanhaOrigemId);
}
