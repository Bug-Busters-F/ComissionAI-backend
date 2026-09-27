package com.bugbusters.backend.store;

import org.springframework.stereotype.Component;

import com.bugbusters.backend.store.dto.StoreResponseDTO;

@Component 
public class StoreMapper {
    public Store toEntity(Integer code, String description) {
        Store newEntity = new Store();
        newEntity.setCode(code);
        newEntity.setDescription(description);
        return newEntity;
    }

    public StoreResponseDTO toResponse (Store brand) {
        return new StoreResponseDTO(
            brand.getId(),
            brand.getCode(),
            brand.getDescription()
        );
    }
}
