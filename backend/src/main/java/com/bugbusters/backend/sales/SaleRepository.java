package com.bugbusters.backend.sales;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SaleRepository extends JpaRepository<Sale, UUID> {
    List<Sale> findBySaleDateBetweenOrderBySaleDateAsc(LocalDate inicio, LocalDate fim);
    @Override
    @EntityGraph(attributePaths = { "registration", "registration.position", "brand", "store" })
    Page<Sale> findAll(Pageable pageable);

    boolean existsByRegistrationId(UUID registrationId);

    boolean existsByBrandId(UUID brandId);

    boolean existsByStoreId(UUID storeId);
}
