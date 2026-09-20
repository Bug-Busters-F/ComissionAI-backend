package com.bugbusters.backend.registration;

import org.springframework.stereotype.Component;

import com.bugbusters.backend.importbase.dto.HrFileRow;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionResolver;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreResolver;

@Component
public class RegistrationMapper {

    private final PositionResolver positionResolver;
    private final StoreResolver storeResolver;

    public RegistrationMapper(PositionResolver positionResolver, StoreResolver storeResolver) {
        this.positionResolver = positionResolver;
        this.storeResolver = storeResolver;
    }

    public Registration toEntity(HrFileRow row) {

        Position position = positionResolver.resolveOrCreate(row.getPositionCode(), row.getPositionDescription());
        Store store = storeResolver.resolveOrCreate(row.getStoreCode(), row.getBrandDescription());

        Registration newRegistration = new Registration();

        newRegistration.setStore(store);
        newRegistration.setPosition(position);
        newRegistration.setRegistration(row.getRegistration());
        newRegistration.setAdmissDate(row.getAdmissDate());
        newRegistration.setDemissDate(row.getDemissDate());

        return newRegistration;
    };
}
