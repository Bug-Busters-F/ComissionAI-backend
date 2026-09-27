package com.bugbusters.backend.basecomiss;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.position.Position;

@Component
public class BaseComissResolver {

    private final BaseComissRepository repository;

    public BaseComissResolver(BaseComissRepository repository) {
        this.repository = repository;
    }

    private BaseComiss createSafely(
            Brand brand,
            Position position,
            BigDecimal percentage,
            LocalDate referenceMonth
    ) {
        try {
            BaseComiss newRecord = new BaseComiss();

            newRecord.setBrand(brand);
            newRecord.setPosition(position);
            newRecord.setPercentage(percentage);
            newRecord.setReferenceMonth(referenceMonth);

            return repository.save(newRecord);

        } catch (DataIntegrityViolationException e) {
            return repository
                    .findByBrandIdAndPositionIdAndReferenceMonth(
                            brand.getId(),
                            position.getId(),
                            referenceMonth
                    )
                    .orElseThrow(() -> e);
        }
    }

    public BaseComiss resolveOrCreate(
            Brand brand,
            Position position,
            BigDecimal percentage,
            LocalDate referenceMonth
    ) {
        return repository
                .findByBrandIdAndPositionIdAndReferenceMonth(
                        brand.getId(),
                        position.getId(),
                        referenceMonth
                )
                .orElseGet(() ->
                        createSafely(
                                brand,
                                position,
                                percentage,
                                referenceMonth
                        )
                );
    }
}