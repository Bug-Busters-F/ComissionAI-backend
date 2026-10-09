package com.bugbusters.backend.registration;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {
    public Optional<Registration> findByRegistration(String registration);

    public Optional<Registration> findByRegistrationIgnoreCase(String registration);

    @org.springframework.data.jpa.repository.Query("""
        SELECT r FROM Registration r
        WHERE (:lojaCodigo IS NULL OR r.store.code = :lojaCodigo)
          AND (:cargoCodigo IS NULL OR r.position.code = :cargoCodigo)
          AND (:matricula IS NULL OR LOWER(r.registration) LIKE LOWER(CONCAT('%', :matricula, '%')))
    """)
    Page<Registration> findByFiltros(
            @org.springframework.data.repository.query.Param("lojaCodigo") Integer lojaCodigo,
            @org.springframework.data.repository.query.Param("cargoCodigo") Integer cargoCodigo,
            @org.springframework.data.repository.query.Param("matricula") String matricula,
            org.springframework.data.domain.Pageable pageable);

    boolean existsByStoreId(UUID storeId);

    boolean existsByPositionId(UUID positionId);
}


