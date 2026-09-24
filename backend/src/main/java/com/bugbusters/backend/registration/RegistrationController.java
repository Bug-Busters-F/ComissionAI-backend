package com.bugbusters.backend.registration;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.registration.dto.RegistrationResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Operation(summary = "Excluir matrícula", description = "Remove uma matrícula, desde que não haja vendas vinculadas.")
    @ApiResponse(responseCode = "204", description = "Matrícula excluída com sucesso")
    @ApiResponse(responseCode = "404", description = "Matrícula não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Matrícula possui vendas vinculadas", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
