package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.SnapshotResultadoCalculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SnapshotResultadoCalculoRepository extends JpaRepository<SnapshotResultadoCalculo, UUID> {
    List<SnapshotResultadoCalculo> findByIdVendaOrderByPreservadoEmDesc(UUID idVenda);
    List<SnapshotResultadoCalculo> findByProtocoloCalculo(UUID protocoloCalculo);
    List<SnapshotResultadoCalculo> findByLotePreparacaoIdOrderByPreservadoEmDesc(UUID lotePreparacaoId);
}
