package com.bugbusters.backend.registration;

import java.util.Date;

import org.springframework.dao.DataIntegrityViolationException;

import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.store.Store;

public class RegistrationResolver {
    private final RegistrationRepository repository;

    public RegistrationResolver(RegistrationRepository repository) {
        this.repository = repository;
    }

    private Registration createSafely(
            Position position,
            Store store,
            String registrationString,
            Date admiss_date,
            Date demissDate
        ) {
        try {
            Registration newRecord = new Registration();
            newRecord.setPosition(position);
            newRecord.setStore(store);
            newRecord.setRegistration(registrationString);
            newRecord.setAdmissDate(admiss_date);
            newRecord.setDemissDate(demissDate);
            return repository.save(newRecord);
        } catch (DataIntegrityViolationException e) {
            return repository.findByRegistration(registrationString).orElseThrow(() -> e);
        }
    }

    public Registration resolveOrCreate(Position position,
            Store store,
            String registrationString,
            Date admiss_date,
            Date demissDate
        ) {
            return repository.findByRegistration(registrationString).orElseGet(() -> createSafely(position, store, registrationString, admiss_date, demissDate));
    }

}
