package com.bugbusters.backend.position;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, UUID> {
    public Optional<Position> findByCode(String code);
}
