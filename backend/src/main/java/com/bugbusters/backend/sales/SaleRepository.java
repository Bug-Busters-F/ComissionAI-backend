package com.bugbusters.backend.sales;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SaleRepository extends JpaRepository<Sale, UUID> {
    List<Sale> findBySaleDateBetweenOrderBySaleDateAsc(LocalDate inicio, LocalDate fim);
}
