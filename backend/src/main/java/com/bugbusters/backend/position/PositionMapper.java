package com.bugbusters.backend.position;

import org.springframework.stereotype.Component;

import com.bugbusters.backend.position.dto.PositionResponseDTO;

@Component 
public class PositionMapper {
    public Position toEntity(Integer code, String description) {
        Position newEntity = new Position();
        newEntity.setCode(code);
        newEntity.setDescription(description);
        return newEntity;
    }

    public PositionResponseDTO toResponse (Position position) {
        return new PositionResponseDTO(
            position.getId(),
            position.getCode(),
            position.getDescription()
        );
    }
}
