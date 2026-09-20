package com.bugbusters.backend.store;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component 
public class StoreResolver {
    private final StoreRepository repository;

    public StoreResolver(StoreRepository repository) {
        this.repository = repository;
    }

    private Store createSafely(Integer code, String description){
        try {
            Store newRecord = new Store();
            newRecord.setCode(code);
            newRecord.setDescription(description);
            return repository.save(newRecord);
        } catch (DataIntegrityViolationException e) {
            return repository.findByCode(code).orElseThrow(() -> e);
        }
    }

    public Store resolveOrCreate(Integer code, String description){
        return repository.findByCode(code).orElseGet(() -> createSafely(code, description));
    };
}
