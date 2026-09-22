package com.bugbusters.backend.store;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, UUID> {
    public Optional<Store> findByCode(Integer code);
}
