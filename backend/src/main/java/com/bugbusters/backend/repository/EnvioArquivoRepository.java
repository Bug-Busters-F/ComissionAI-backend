package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.EnvioArquivo;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvioArquivoRepository extends JpaRepository<EnvioArquivo, Long> {

    Optional<EnvioArquivo> findByReportId(Long reportId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EnvioArquivo e WHERE e.reportId = :reportId")
    Optional<EnvioArquivo> findByReportIdForUpdate(@Param("reportId") Long reportId);

    Optional<EnvioArquivo> findByIdLote(String idLote);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EnvioArquivo e WHERE e.idLote = :idLote")
    Optional<EnvioArquivo> findByIdLoteForUpdate(@Param("idLote") String idLote);

    @Query("""
        SELECT e FROM EnvioArquivo e
        WHERE (:tipoBase IS NULL OR e.tipoBase = :tipoBase)
          AND (:status IS NULL OR e.status = :status)
          AND (:competencia IS NULL OR e.competencia = :competencia)
        ORDER BY e.criadoEm DESC
    """)
    Page<EnvioArquivo> findByFiltros(
            @Param("tipoBase") String tipoBase,
            @Param("status") String status,
            @Param("competencia") String competencia,
            Pageable pageable
    );

    @Query("""
        SELECT e FROM EnvioArquivo e
        WHERE e.hashConteudo = :hashConteudo
          AND e.tipoBase = :tipoBase
          AND ((:competencia IS NULL AND e.competencia IS NULL) OR e.competencia = :competencia)
          AND e.status IN ('PENDENTE', 'PROCESSANDO', 'CONCLUIDO')
        ORDER BY e.criadoEm DESC
    """)
    List<EnvioArquivo> findLotesAtivosPorHash(
            @Param("hashConteudo") String hashConteudo,
            @Param("tipoBase") String tipoBase,
            @Param("competencia") String competencia
    );

    List<EnvioArquivo> findByTipoBaseIgnoreCase(String tipoBase);

    List<EnvioArquivo> findByTipoBaseIgnoreCaseAndCompetencia(String tipoBase, String competencia);

    @Query("""
        SELECT e FROM EnvioArquivo e
        WHERE UPPER(e.tipoBase) = UPPER(:tipoBase)
          AND (:competencia IS NULL OR e.competencia = :competencia)
          AND e.status = 'CONCLUIDO'
          AND e.excluidoEm IS NULL
          AND (e.rejeicaoIntegral IS NULL OR e.rejeicaoIntegral = false)
          AND e.substituidoPorId IS NULL
        ORDER BY e.criadoEm DESC
    """)
    List<EnvioArquivo> findLotesConcluidosValidos(
            @Param("tipoBase") String tipoBase,
            @Param("competencia") String competencia
    );

    @Query("SELECT COALESCE(MAX(e.reportId), 0) FROM EnvioArquivo e")
    Long findMaxReportId();

    Optional<EnvioArquivo> findFirstByArquivoImportacaoId(java.util.UUID arquivoImportacaoId);
}
