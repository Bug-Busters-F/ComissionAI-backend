package com.bugbusters.backend.store;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.bugbusters.backend.store.dto.StoreResponseDTO;

@Service 
public class StoreService {
    private final StoreRepository repository;
    private final StoreMapper mapper;

    public StoreService(StoreRepository repository, StoreMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Page<StoreResponseDTO> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toResponse);
    }
}
