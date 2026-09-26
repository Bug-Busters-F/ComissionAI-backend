package com.bugbusters.backend.brand;

import org.springframework.stereotype.Component;

import com.bugbusters.backend.brand.dto.BrandResponseDTO;

@Component
public class BrandMapper {
    public Brand toEntity(Integer code, String description) {
        Brand newEntity = new Brand();
        newEntity.setCode(code);
        newEntity.setDescription(description);
        return  newEntity;
    }

    public BrandResponseDTO toResponse (Brand brand) {
        return new BrandResponseDTO(
            brand.getId(),
            brand.getCode(),
            brand.getDescription()
        );
    }
}
