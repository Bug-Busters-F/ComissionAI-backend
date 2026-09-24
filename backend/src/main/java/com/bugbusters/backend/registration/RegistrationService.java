package com.bugbusters.backend.registration;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.registration.dto.RegistrationResponseDTO;
import com.bugbusters.backend.sales.SaleRepository;

import jakarta.transaction.Transactional;

@Service
public class RegistrationService {

    private final RegistrationRepository repository;
    private final RegistrationMapper mapper;
    private final SaleRepository saleRepository;

    public RegistrationService(
            RegistrationRepository repository,
            RegistrationMapper mapper,
            SaleRepository saleRepository
        ) {
        this.repository = repository;
        this.mapper = mapper;
        this.saleRepository = saleRepository;
    }

    public Page<RegistrationResponseDTO> findAll(Pageable pageable) {
        return repository.findAll(pageable)
                .map(mapper::toResponse);
    }

    /**
     * Exclui uma matrícula pelo ID.
     *
     * @throws ResourceNotFoundException se a matrícula não existir
     * @throws ResourceConflictException se houver vendas vinculadas a ela
     */
    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Matrícula não encontrada: " + id);
        }
        if (saleRepository.existsByRegistrationId(id)) {
            throw new ResourceConflictException(
                    "Não é possível excluir a matrícula " + id + ": existem vendas vinculadas a ela.");
        }
        repository.deleteById(id);
    }
}