package com.bugbusters.backend.registration;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {
    
}
