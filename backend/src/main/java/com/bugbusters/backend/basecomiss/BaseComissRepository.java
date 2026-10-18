package com.bugbusters.backend.basecomiss;

import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

@Repository 
public interface BaseComissRepository extends JpaRepository<BaseComiss, UUID> {
    Optional<BaseComiss> findByBrandIdAndPositionIdAndReferenceMonth(
            UUID brandId,
            UUID positionId,
            LocalDate referenceMonth
    );

    Optional<BaseComiss> findFirstByBrandIdAndPositionIdAndReferenceMonth(
            UUID brandId,
            UUID positionId,
            LocalDate referenceMonth
    );

    Optional<BaseComiss> findFirstByBrandIdAndPositionIdOrderByReferenceMonthDesc(
            UUID brandId,
            UUID positionId
    );

    Optional<BaseComiss> findFirstByBrandCodeAndPositionCodeOrderByReferenceMonthDesc(
            Integer brandCode,
            Integer positionCode
    );

    @org.springframework.data.jpa.repository.Query("""
        SELECT b FROM BaseComiss b
        WHERE (:brandCode IS NULL OR b.brand.code = :brandCode)
          AND (:positionCode IS NULL OR b.position.code = :positionCode)
          AND (:referenceMonth IS NULL OR b.referenceMonth = :referenceMonth)
        ORDER BY b.brand.code ASC, b.position.code ASC, b.referenceMonth DESC
    """)
    org.springframework.data.domain.Page<BaseComiss> findByFiltros(
            @org.springframework.data.repository.query.Param("brandCode") Integer brandCode,
            @org.springframework.data.repository.query.Param("positionCode") Integer positionCode,
            @org.springframework.data.repository.query.Param("referenceMonth") LocalDate referenceMonth,
            org.springframework.data.domain.Pageable pageable
    );

    @org.springframework.data.jpa.repository.Query("""
        SELECT b FROM BaseComiss b
        WHERE (:brandCode IS NULL OR b.brand.code = :brandCode)
          AND (:positionCode IS NULL OR b.position.code = :positionCode)
        ORDER BY b.brand.code ASC, b.position.code ASC, b.referenceMonth DESC
    """)
    java.util.List<BaseComiss> findAllByFiltros(
            @org.springframework.data.repository.query.Param("brandCode") Integer brandCode,
            @org.springframework.data.repository.query.Param("positionCode") Integer positionCode
    );

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT YEAR(b.referenceMonth), MONTH(b.referenceMonth) FROM BaseComiss b ORDER BY 1 DESC, 2 DESC")
    java.util.List<Object[]> findDistinctAnosEMesesBaseComiss();

    @org.springframework.data.jpa.repository.Query("SELECT count(b) FROM BaseComiss b WHERE b.referenceMonth BETWEEN :inicio AND :fim")
    long countByReferenceMonthBetween(@org.springframework.data.repository.query.Param("inicio") LocalDate inicio, @org.springframework.data.repository.query.Param("fim") LocalDate fim);

    @org.springframework.data.jpa.repository.Query("SELECT b FROM BaseComiss b JOIN FETCH b.brand JOIN FETCH b.position ORDER BY b.referenceMonth DESC")
    java.util.List<BaseComiss> findAllComRelacionamentos();
}

