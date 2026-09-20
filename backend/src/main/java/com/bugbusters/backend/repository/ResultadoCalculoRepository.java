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
}
