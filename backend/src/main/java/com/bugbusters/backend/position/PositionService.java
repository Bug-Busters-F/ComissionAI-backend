package com.bugbusters.backend.position;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.position.dto.PositionResponseDTO;
import com.bugbusters.backend.registration.RegistrationRepository;

import jakarta.transaction.Transactional;

@Service
public class PositionService {
    private final PositionMapper mapper;
    private final PositionRepository repository;
    private final RegistrationRepository registrationRepository;

    public PositionService(
            PositionMapper mapper,
            PositionRepository repository,
            RegistrationRepository registrationRepository) {
        this.mapper = mapper;
        this.repository = repository;
        this.registrationRepository = registrationRepository;
    }

    public Page<PositionResponseDTO> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toResponse);
    }

    /**
     * @throws ResourceNotFoundException 
     * @throws ResourceConflictException 
     */
    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Cargo não encontrado: " + id);
        }
        if (registrationRepository.existsByPositionId(id)) {
            throw new ResourceConflictException(
                    "Não é possível excluir o cargo " + id + ": existem matrículas vinculadas a ele.");
        }
        repository.deleteById(id);
    }
}
