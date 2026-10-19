package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.InconsistenciaImportacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InconsistenciaImportacaoRepository extends JpaRepository<InconsistenciaImportacao, Long> {
    List<InconsistenciaImportacao> findByEnvioId(Long envioId);
}
