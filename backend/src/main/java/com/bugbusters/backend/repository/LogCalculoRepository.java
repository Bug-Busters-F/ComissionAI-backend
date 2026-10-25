package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.LogCalculoImutavel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LogCalculoRepository extends JpaRepository<LogCalculoImutavel, UUID>, JpaSpecificationExecutor<LogCalculoImutavel> {

    /**
     * Recupera todos os logs imutáveis ordenados do mais recente para o mais antigo.
     */
    List<LogCalculoImutavel> findAllByOrderByExecutadoEmDesc();

    /**
     * Busca logs associados a um protocolo específico de cálculo.
     */
    List<LogCalculoImutavel> findByProtocolo(UUID protocolo);

    /**
     * Busca logs associados à matrícula de um colaborador.
     */
    List<LogCalculoImutavel> findByMatricula(String matricula);

    boolean existsByIdLoteOrigem(String idLoteOrigem);

    List<LogCalculoImutavel> findTop5ByIdLoteOrigem(String idLoteOrigem);

    boolean existsByIdVendaIn(java.util.Collection<UUID> idsVendas);

    List<LogCalculoImutavel> findTop5ByIdVendaIn(java.util.Collection<UUID> idsVendas);

    boolean existsByMatriculaIn(java.util.Collection<String> matriculas);

    List<LogCalculoImutavel> findTop5ByMatriculaIn(java.util.Collection<String> matriculas);

    @org.springframework.data.jpa.repository.Query("""
        SELECT COUNT(l) FROM LogCalculoImutavel l
        WHERE l.codMarca = :codMarca
          AND l.codCargo = :codCargo
          AND l.dataVenda BETWEEN :inicio AND :fim
    """)
    long countByMarcaCargoEPeriodo(
            @org.springframework.data.repository.query.Param("codMarca") Integer codMarca,
            @org.springframework.data.repository.query.Param("codCargo") Integer codCargo,
            @org.springframework.data.repository.query.Param("inicio") java.time.LocalDate inicio,
            @org.springframework.data.repository.query.Param("fim") java.time.LocalDate fim
    );

    /**
     * Blindagem de repositório contra exclusão acidental de registros imutáveis (BUG-23).
     */
    @Override
    default void delete(LogCalculoImutavel entity) {
        throw new UnsupportedOperationException("Registros de log de cálculo imutável não podem ser excluídos.");
    }

    /**
     * Blindagem de repositório contra exclusão acidental por ID de registros imutáveis (BUG-23).
     */
    @Override
    default void deleteById(UUID id) {
        throw new UnsupportedOperationException("Registros de log de cálculo imutável não podem ser excluídos.");
    }
}
