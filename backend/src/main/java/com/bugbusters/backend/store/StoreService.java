package com.bugbusters.backend.store;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.dto.StoreResponseDTO;

import jakarta.transaction.Transactional;

@Service
public class StoreService {
    private final StoreRepository repository;
    private final StoreMapper mapper;
    private final SaleRepository saleRepository;
    private final RegistrationRepository registrationRepository;

    public StoreService(
            StoreRepository repository,
            StoreMapper mapper,
            SaleRepository saleRepository,
            RegistrationRepository registrationRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.saleRepository = saleRepository;
        this.registrationRepository = registrationRepository;
    }

    public Page<StoreResponseDTO> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toResponse);
    }

    /**
     * @throws ResourceNotFoundException 
     * @throws ResourceConflictException 
     */
    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Loja não encontrada: " + id);
        }
        if (saleRepository.existsByStoreId(id)) {
            throw new ResourceConflictException(
                    "Não é possível excluir a loja " + id + ": existem vendas vinculadas a ela.");
        }
        if (registrationRepository.existsByStoreId(id)) {
            throw new ResourceConflictException(
                    "Não é possível excluir a loja " + id + ": existem matrículas vinculadas a ela.");
        }
        repository.deleteById(id);
    }
}
