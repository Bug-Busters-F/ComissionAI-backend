package com.bugbusters.backend.position;

import org.springframework.stereotype.Component;

@Component 
public class PositionMapper {
    public Position toEntity(Integer code, String description) {
        Position newEntity = new Position();
        newEntity.setCode(code);
        newEntity.setDescription(description);
        return newEntity;
    }
}
