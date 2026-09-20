package com.bugbusters.backend.brand;

import org.springframework.stereotype.Component;

@Component 
public class BrandMapper {
    public Brand toEntity(Integer code, String description) {
        Brand newEntity = new Brand();
        newEntity.setCode(code);
        newEntity.setDescription(description);
        return  newEntity;
    }
}
