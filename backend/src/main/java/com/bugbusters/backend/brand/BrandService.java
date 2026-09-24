package com.bugbusters.backend.brand;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.bugbusters.backend.brand.dto.BrandResponseDTO;
import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.sales.SaleRepository;

import jakarta.transaction.Transactional;

@Service 
public class BrandService {
    private final BrandRepository repository;
    private final BrandMapper mapper;
    private final SaleRepository saleRepository;


    public BrandService(BrandRepository repository, BrandMapper mapper, SaleRepository saleRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.saleRepository = saleRepository;
    }


    public Page<BrandResponseDTO> findAllBrands(Pageable pageable) { 
        return repository.findAll(pageable).map(mapper::toResponse);
    };

    /**
     * @throws ResourceNotFoundException 
     * @throws ResourceConflictException
     */
    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Marca não encontrada: " + id);
        }
        if (saleRepository.existsByBrandId(id)) {
            throw new ResourceConflictException(
                    "Não é possível excluir a marca " + id + ": existem vendas vinculadas a ela.");
        }
        repository.deleteById(id);
    }
}
