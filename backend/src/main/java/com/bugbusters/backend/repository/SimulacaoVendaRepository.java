package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.SimulacaoVenda;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SimulacaoVendaRepository extends JpaRepository<SimulacaoVenda, UUID>, JpaSpecificationExecutor<SimulacaoVenda> {

    Page<SimulacaoVenda> findBySimulacaoIdOrderByDataVendaAsc(UUID simulacaoId, Pageable pageable);

    Optional<SimulacaoVenda> findBySimulacaoIdAndIdVenda(UUID simulacaoId, UUID idVenda);

    long countBySimulacaoId(UUID simulacaoId);
}
