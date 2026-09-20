package com.bugbusters.backend.position;


import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component 
public class PositionResolver {
    private final PositionRepository repository;
    private final PositionMapper mapper;

    public PositionResolver(PositionRepository repository, PositionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    private Position createSafely(Integer code, String description){
        try {
            Position newRecord = mapper.toEntity(code, description);
            return repository.save(newRecord);
        } catch (DataIntegrityViolationException e) {
            return repository.findByCode(code).orElseThrow(() -> e);
        }
    }

    public Position resolveOrCreate(Integer code, String description){
        return repository.findByCode(code).orElseGet(() -> createSafely(code, description));
    }

     public Position resolve(Integer code) {
        return repository.findByCode(code)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Cargo não encontrado para o código: " + code
                        )
                );
    }
}
