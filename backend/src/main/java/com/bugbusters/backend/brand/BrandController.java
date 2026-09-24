package com.bugbusters.backend.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bugbusters.backend.brand.dto.BrandResponseDTO;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController 
@RequestMapping ("api/v1/marcas")
public class BrandController {
    private final BrandService service;

    

    public BrandController(BrandService service) {
        this.service = service;
    }


    @ApiResponse(responseCode = "200", description = "Marcas recuperadas com sucesso")
    @GetMapping 
    public Page<BrandResponseDTO> findAll(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "0")int size){
        Pageable pageable = PageRequest.of(page, size);
        return service.findAllBrands(pageable);
    }
}
