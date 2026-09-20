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
}
