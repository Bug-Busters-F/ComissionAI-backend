package com.bugbusters.backend.store;

import org.springframework.stereotype.Component;

@Component 
public class StoreMapper {
    public Store toEntity(Integer code, String description) {
        Store newEntity = new Store();
        newEntity.setCode(code);
        newEntity.setDescription(description);
        return newEntity;
    }
}
