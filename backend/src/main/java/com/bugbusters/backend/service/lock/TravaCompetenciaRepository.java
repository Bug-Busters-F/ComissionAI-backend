package com.bugbusters.backend.service.lock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TravaCompetenciaRepository extends JpaRepository<TravaCompetenciaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TravaCompetenciaEntity t WHERE t.competencia = :competencia")
    Optional<TravaCompetenciaEntity> findByCompetenciaForUpdate(@Param("competencia") String competencia);
}
