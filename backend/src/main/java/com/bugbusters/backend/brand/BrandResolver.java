package com.bugbusters.backend.brand;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component 
public class BrandResolver {
    private final BrandRepository repository;
    private final BrandMapper mapper;

    public BrandResolver(BrandRepository repository, BrandMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Brand createSafely(Integer brandCode, String brandDescription) {
        try {
            Brand newRecord = mapper.toEntity(brandCode, brandDescription);
            return repository.save(newRecord);
        } catch (DataIntegrityViolationException e) {
            return repository.findByCode(brandCode).orElseThrow(() -> e);
        }
    }

    public Brand resolve(Integer brandCode) {
    return repository.findByCode(brandCode)
            .orElseThrow(() -> new RuntimeException(
                    "Marca não encontrada para o código: " + brandCode
            ));
    }

    public Brand resolveOrCreate(Integer brandCode, String brandDescription) {
        return repository.findByCode(brandCode).orElseGet(() -> createSafely(brandCode, brandDescription));
    }
}
