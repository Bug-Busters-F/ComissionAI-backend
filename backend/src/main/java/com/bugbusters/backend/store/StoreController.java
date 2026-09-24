package com.bugbusters.backend.store;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bugbusters.backend.store.dto.StoreResponseDTO;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController 
@RequestMapping ("api/v1/lojas")
public class StoreController {
    private final StoreService service;

    public StoreController(StoreService service) {
        this.service = service;
    }

    @ApiResponse(responseCode = "200", description = "Marcas recuperadas com sucesso")
    @GetMapping
    public Page<StoreResponseDTO> findAll (
        @RequestParam int page, @RequestParam int size
    ){
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    };
}
