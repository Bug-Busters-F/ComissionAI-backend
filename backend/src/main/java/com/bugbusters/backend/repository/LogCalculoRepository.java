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
