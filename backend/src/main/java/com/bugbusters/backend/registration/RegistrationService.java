package com.bugbusters.backend.registration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.bugbusters.backend.registration.dto.RegistrationResponseDTO;

@Service
public class RegistrationService {

    private final RegistrationRepository repository;
    private final RegistrationMapper mapper;

    public RegistrationService(
            RegistrationRepository repository,
            RegistrationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Page<RegistrationResponseDTO> findAll(Pageable pageable) {
        return repository.findAll(pageable)
                .map(mapper::toResponse);
    }
}