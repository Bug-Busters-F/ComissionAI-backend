package com.bugbusters.backend.sales;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

@Repository
public interface SaleRepository extends JpaRepository<Sale, UUID>, JpaSpecificationExecutor<Sale> {
    List<Sale> findBySaleDateBetweenOrderBySaleDateAsc(LocalDate inicio, LocalDate fim);

    @EntityGraph(attributePaths = { "registration", "registration.position", "registration.store", "brand", "store" })
    @Query("SELECT s FROM Sale s WHERE s.saleDate BETWEEN :inicio AND :fim ORDER BY s.saleDate ASC")
    List<Sale> findVendasPorPeriodoComRelacionamentos(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("SELECT DISTINCT YEAR(s.saleDate), MONTH(s.saleDate) FROM Sale s ORDER BY 1 DESC, 2 DESC")
    List<Object[]> findDistinctAnosEMesesVendas();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    java.util.Optional<Sale> findByIdForUpdate(@Param("id") UUID id);

    @Override
    @EntityGraph(attributePaths = { "registration", "registration.position", "registration.store", "brand", "store" })
    java.util.Optional<Sale> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = { "registration", "registration.position", "registration.store", "brand", "store" })
    Page<Sale> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = { "registration", "registration.position", "registration.store", "brand", "store" })
    Page<Sale> findAll(Specification<Sale> spec, Pageable pageable);

    boolean existsByRegistrationId(UUID registrationId);

    boolean existsByBrandId(UUID brandId);

    boolean existsByStoreId(UUID storeId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Sale s WHERE s.saleDate BETWEEN :inicio AND :fim")
    int deleteBySaleDateBetween(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}

