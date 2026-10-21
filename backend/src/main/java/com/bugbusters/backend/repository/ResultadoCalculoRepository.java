package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.ResultadoCalculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResultadoCalculoRepository extends JpaRepository<ResultadoCalculo, Long> {

    /**
     * Busca o resultado do cálculo pelo identificador único da venda (UUID da Sale).
     */
    Optional<ResultadoCalculo> findByIdVenda(UUID idVenda);

    /**
     * Busca o resultado do cálculo pela chave única de negócio (matrícula, data da venda e regra aplicada).
     * Utilizado para garantir a idempotência e proteção contra recálculos duplicados.
     */
    Optional<ResultadoCalculo> findByMatriculaAndDataVendaAndRegraId(String matricula, LocalDate dataVenda, Long regraId);

    /**
     * Busca o resultado mais recente para uma determinada matrícula e data de venda.
     */
    Optional<ResultadoCalculo> findTopByMatriculaAndDataVendaOrderByCalculadoEmDesc(String matricula, LocalDate dataVenda);

    /**
     * Busca um resultado específico pelo UUID do protocolo.
     */
    Optional<ResultadoCalculo> findByProtocoloCalculo(UUID protocoloCalculo);

    /**
     * Lista todos os cálculos associados a uma matrícula.
     */
    List<ResultadoCalculo> findByMatricula(String matricula);

    /**
     * Busca resultados de cálculo em lote para uma coleção de IDs de venda (Otimização BACK-14).
     */
    List<ResultadoCalculo> findByIdVendaIn(java.util.Collection<UUID> idsVendas);

    boolean existsByIdVendaIn(java.util.Collection<UUID> idsVendas);

    List<ResultadoCalculo> findTop5ByIdVendaIn(java.util.Collection<UUID> idsVendas);

    boolean existsByMatriculaIn(java.util.Collection<String> matriculas);

    List<ResultadoCalculo> findTop5ByMatriculaIn(java.util.Collection<String> matriculas);

    @org.springframework.data.jpa.repository.Query("""
        SELECT COUNT(r) FROM ResultadoCalculo r
        WHERE r.codMarca = :codMarca
          AND r.codCargo = :codCargo
          AND r.dataVenda BETWEEN :inicio AND :fim
    """)
    long countByMarcaCargoEPeriodo(
            @org.springframework.data.repository.query.Param("codMarca") Integer codMarca,
            @org.springframework.data.repository.query.Param("codCargo") Integer codCargo,
            @org.springframework.data.repository.query.Param("inicio") LocalDate inicio,
            @org.springframework.data.repository.query.Param("fim") LocalDate fim
    );
}
