package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.ExecucaoCalculo;
import com.bugbusters.backend.model.StatusExecucaoCalculo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ExecucaoCalculoRepository extends JpaRepository<ExecucaoCalculo, UUID> {
    Optional<ExecucaoCalculo> findByChaveIdempotencia(String chaveIdempotencia);
    Optional<ExecucaoCalculo> findFirstByCompetenciaAndStatusInOrderBySolicitadoEmDesc(String competencia, Collection<StatusExecucaoCalculo> status);
}
