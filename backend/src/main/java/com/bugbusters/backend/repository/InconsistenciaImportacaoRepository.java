package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.InconsistenciaImportacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface InconsistenciaImportacaoRepository extends JpaRepository<InconsistenciaImportacao, Long> {
    List<InconsistenciaImportacao> findByEnvioId(Long envioId);

    @Query("SELECT i FROM InconsistenciaImportacao i JOIN FETCH i.envio WHERE i.envio.id IN :envioIds ORDER BY i.id ASC")
    List<InconsistenciaImportacao> findByEnvioIdIn(@Param("envioIds") Collection<Long> envioIds);

    @Query(value = "SELECT i FROM InconsistenciaImportacao i JOIN FETCH i.envio WHERE i.envio.id IN :envioIds ORDER BY i.id ASC",
           countQuery = "SELECT COUNT(i) FROM InconsistenciaImportacao i WHERE i.envio.id IN :envioIds")
    Page<InconsistenciaImportacao> findByEnvioIdIn(@Param("envioIds") Collection<Long> envioIds, Pageable pageable);
}
