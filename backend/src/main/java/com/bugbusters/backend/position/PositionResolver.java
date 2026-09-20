package com.bugbusters.backend.position;

import org.springframework.dao.DataIntegrityViolationException;

public class PositionResolver {
    private final PositionRepository repository;

    public PositionResolver(PositionRepository repository) {
        this.repository = repository;
    }

    private Position createSafely(String code, String description){
        try {
            Position newRecord = new Position();
            newRecord.setCode(code);
            newRecord.setDescription(description);
            return repository.save(newRecord);
        } catch (DataIntegrityViolationException e) {
            return repository.findByCode(code).orElseThrow(() -> e);
        }
    }

    public Position resolveOrCreate(String code, String description){
        return repository.findByCode(code).orElseGet(() -> createSafely(code, description));
    }
}
