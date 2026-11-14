package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.CampanhaAnexoBase;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampanhaAnexoBaseRepository extends JpaRepository<CampanhaAnexoBase, UUID> {

    @Query("SELECT a FROM CampanhaAnexoBase a JOIN FETCH a.envioArquivo WHERE a.campanha.id = :campanhaId AND a.desanexadoEm IS NULL ORDER BY a.anexadoEm DESC")
    List<CampanhaAnexoBase> findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(@Param("campanhaId") Long campanhaId);

    @Query("SELECT a FROM CampanhaAnexoBase a JOIN FETCH a.envioArquivo WHERE a.campanha.id = :campanhaId AND a.competencia = :competencia AND a.tipoBase = :tipoBase AND a.desanexadoEm IS NULL")
    List<CampanhaAnexoBase> findByCampanhaIdAndCompetenciaAndTipoBaseAndDesanexadoEmIsNull(
            @Param("campanhaId") Long campanhaId,
            @Param("competencia") String competencia,
            @Param("tipoBase") String tipoBase
    );

    @Query("SELECT a FROM CampanhaAnexoBase a JOIN FETCH a.envioArquivo WHERE a.campanha.id = :campanhaId AND a.tipoBase = :tipoBase AND a.desanexadoEm IS NULL")
    List<CampanhaAnexoBase> findByCampanhaIdAndTipoBaseAndDesanexadoEmIsNull(
            @Param("campanhaId") Long campanhaId,
            @Param("tipoBase") String tipoBase
    );

    boolean existsByCampanhaIdAndEnvioArquivoIdAndDesanexadoEmIsNull(Long campanhaId, Long envioArquivoId);

    Optional<CampanhaAnexoBase> findByIdAndCampanhaIdAndDesanexadoEmIsNull(UUID id, Long campanhaId);

    @Query("SELECT a FROM CampanhaAnexoBase a JOIN FETCH a.campanha c WHERE a.envioArquivo.id = :envioArquivoId AND a.desanexadoEm IS NULL AND c.removidoEm IS NULL")
    List<CampanhaAnexoBase> findAnexosAtivosDeCampanhasNaoRemovidas(@Param("envioArquivoId") Long envioArquivoId);
}
