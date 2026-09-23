package com.bugbusters.backend.registration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bugbusters.backend.registration.dto.RegistrationResponseDTO;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/matriculas")
@Tag(name = "Matriculas", description = "Registro de matriculas de colaboradores")
public class RegistrationController {
    private final RegistrationService service;

    public RegistrationController(RegistrationService service) {
        this.service = service;
    }

    @ApiResponse(responseCode = "200", description = "Vendas recuperadas com sucesso")
    @GetMapping
    public Page<RegistrationResponseDTO> findAllSales(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }
}
