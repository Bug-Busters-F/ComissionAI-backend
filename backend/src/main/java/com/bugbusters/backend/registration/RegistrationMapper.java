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
    private final RegistrationRepository registrationRepository;

    public RegistrationMapper(PositionResolver positionResolver,
                               StoreResolver storeResolver,
                               RegistrationRepository registrationRepository) {
        this.positionResolver = positionResolver;
        this.storeResolver = storeResolver;
        this.registrationRepository = registrationRepository;
    }

    public Registration toEntity(HrFileRow row) {
        Position position = positionResolver.resolveOrCreate(row.getPositionCode(), row.getPositionDescription());
        Store store = storeResolver.resolveOrCreate(row.getStoreCode(), row.getStoreDescription());

        Registration registration = registrationRepository.findByRegistration(row.getRegistration())
            .orElseGet(Registration::new);

        registration.setAdmissDate(row.getAdmissDate());
        registration.setDemissDate(row.getDemissDate());
        registration.setPosition(position);
        registration.setStore(store);

        return registration;
    }
}
