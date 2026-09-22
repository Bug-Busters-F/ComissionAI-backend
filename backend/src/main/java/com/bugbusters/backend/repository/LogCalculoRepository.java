package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.LogCalculoImutavel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LogCalculoRepository extends JpaRepository<LogCalculoImutavel, UUID> {

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
}
