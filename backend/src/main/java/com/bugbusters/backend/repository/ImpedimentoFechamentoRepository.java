package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.ImpedimentoFechamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ImpedimentoFechamentoRepository
        extends JpaRepository<ImpedimentoFechamento, UUID>, JpaSpecificationExecutor<ImpedimentoFechamento> {

    long countByFechamentoId(UUID fechamentoId);
}
