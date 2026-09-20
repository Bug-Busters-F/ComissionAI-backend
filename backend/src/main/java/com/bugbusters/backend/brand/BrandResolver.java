package com.bugbusters.backend.brand;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component 
public class BrandResolver {
    private final BrandRepository repository;

    public BrandResolver(BrandRepository repository) {
        this.repository = repository;
    }

    public Brand createSafely(Integer brandCode, String brandDescription) {
        try {
            Brand newRecord = new Brand();
            newRecord.setCode(brandCode);
            newRecord.setDescription(brandDescription);
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
