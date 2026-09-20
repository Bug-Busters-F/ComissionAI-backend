package com.bugbusters.backend.store;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component 
public class StoreResolver {
    private final StoreRepository repository;
    private final StoreMapper mapper;

    public StoreResolver(StoreRepository repository, StoreMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    private Store createSafely(Integer code, String description){
        try {
            Store newRecord = mapper.toEntity(code, description);
            return repository.save(newRecord);
        } catch (DataIntegrityViolationException e) {
            return repository.findByCode(code).orElseThrow(() -> e);
        }
    }

    public Store resolve(Integer code) {
    return repository.findByCode(code)
            .orElseThrow(() -> new RuntimeException(
                    "Loja não encontrada para o código: " + code
            ));
    }

    public Store resolveOrCreate(Integer code, String description){
        return repository.findByCode(code).orElseGet(() -> createSafely(code, description));
    };
}
