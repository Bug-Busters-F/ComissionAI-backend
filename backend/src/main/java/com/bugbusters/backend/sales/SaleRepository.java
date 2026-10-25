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
    @org.springframework.data.jpa.repository.Query("""
        select s from Sale s
        left join fetch s.brand left join fetch s.store
        left join fetch s.registration r left join fetch r.position left join fetch r.store
        where s.saleDate between :inicio and :fim
        order by s.saleDate, s.id
        """)
    java.util.List<Sale> referencias(@org.springframework.data.repository.query.Param("inicio") java.time.LocalDate inicio,
                                    @org.springframework.data.repository.query.Param("fim") java.time.LocalDate fim);

    @EntityGraph(attributePaths = { "registration", "registration.position", "registration.store", "brand", "store" })
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

    boolean existsByRegistrationIdIn(java.util.Collection<UUID> registrationIds);

    boolean existsByBrandId(UUID brandId);

    boolean existsByStoreId(UUID storeId);

    List<Sale> findByIdLoteOrigem(String idLoteOrigem);

    long countByIdLoteOrigem(String idLoteOrigem);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("DELETE FROM Sale s WHERE s.idLoteOrigem = :idLoteOrigem")
    int deleteByIdLoteOrigem(@Param("idLoteOrigem") String idLoteOrigem);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("DELETE FROM Sale s WHERE s.saleDate BETWEEN :inicio AND :fim")
    int deleteBySaleDateBetween(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
