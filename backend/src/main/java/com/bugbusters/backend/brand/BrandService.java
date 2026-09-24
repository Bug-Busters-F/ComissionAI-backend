package com.bugbusters.backend.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.bugbusters.backend.brand.dto.BrandResponseDTO;

@Service 
public class BrandService {
    private final BrandRepository repository;
    private final BrandMapper mapper;


    public BrandService(BrandRepository repository, BrandMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }


    public Page<BrandResponseDTO> findAllBrands(Pageable pageable) { 
        return repository.findAll(pageable).map(mapper::toResponse);
    };
}
