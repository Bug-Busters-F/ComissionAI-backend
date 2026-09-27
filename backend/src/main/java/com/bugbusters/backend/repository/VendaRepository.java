package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VendaRepository extends JpaRepository<Venda, Long> {

    /**
     * Verifica a existência de uma venda pelo identificador externo.
     * Utilizado para detectar duplicatas e garantir idempotência.
     *
     * @param idVendaExterno identificador externo fornecido pelo cliente
     * @return {@code Optional} contendo a venda, caso já registrada
     */
    Optional<Venda> findByIdVendaExterno(String idVendaExterno);

    /**
     * Verifica se já existe uma venda com o identificador externo informado.
     *
     * @param idVendaExterno identificador externo
     * @return {@code true} se já existe, {@code false} caso contrário
     */
    boolean existsByIdVendaExterno(String idVendaExterno);
}
