package com.bugbusters.backend.position;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, UUID> {
    
}
