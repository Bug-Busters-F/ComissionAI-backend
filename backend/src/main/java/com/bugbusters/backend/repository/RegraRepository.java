package com.bugbusters.backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.Regra;

@Repository
public interface RegraRepository extends JpaRepository<Regra, Long> {
    Optional<Regra> findByCampanhaIdAndRemovidoEmIsNull(Long campanhaId);

    @Query("""
        SELECT r FROM Regra r
        WHERE r.status = :status
          AND r.removidoEm IS NULL
          AND r.dataInicio <= :dataVenda
          AND r.dataFim >= :dataVenda
          AND (r.codMarca IS NULL OR r.codMarca = :codMarca)
          AND (r.codLoja IS NULL OR r.codLoja = :codLoja)
          AND (r.codCargo IS NULL OR r.codCargo = :codCargo)
          AND (r.matricula IS NULL OR r.matricula = :matricula)
          AND (r.canal IS NULL OR LOWER(r.canal) = LOWER(:canal))
        ORDER BY r.id DESC
    """)
    List<Regra> findRegrasAplicaveis(
            @Param("dataVenda") LocalDate dataVenda,
            @Param("codMarca") Integer codMarca,
            @Param("codLoja") Integer codLoja,
            @Param("codCargo") Integer codCargo,
            @Param("matricula") String matricula,
            @Param("canal") String canal,
            @Param("status") StatusRegra status
    );
}