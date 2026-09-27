package com.bugbusters.backend.registration;

import org.springframework.stereotype.Component;

import com.bugbusters.backend.importbase.dto.HrFileRow;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionResolver;
import com.bugbusters.backend.registration.dto.RegistrationResponseDTO;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreResolver;

@Component
public class RegistrationMapper {

    private final StoreResolver storeResolver;
    private final PositionResolver positionResolver;

    public RegistrationMapper(StoreResolver storeResolver, PositionResolver positionResolver) {
        this.storeResolver = storeResolver;
        this.positionResolver = positionResolver;
    }

    public Registration toEntity(HrFileRow row) {
        Store store = storeResolver.resolveOrCreate(row.getStoreCode(), row.getStoreDescription());
        Position position = positionResolver.resolveOrCreate(row.getPositionCode(), row.getPositionDescription());

        Registration registration = new Registration();
        registration.setRegistration(row.getRegistration());
        registration.setAdmissDate(row.getAdmissDate());
        registration.setDemissDate(row.getDemissDate());
        registration.setStore(store);
        registration.setPosition(position);

        return registration;
    }

    public RegistrationResponseDTO toResponse(Registration entity) {
        return new RegistrationResponseDTO(
                entity.getId(),
                entity.getPosition(),
                entity.getStore(),
                entity.getAdmissDate(),
                entity.getDemissDate());
    }
}