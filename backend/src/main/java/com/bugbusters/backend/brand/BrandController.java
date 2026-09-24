package com.bugbusters.backend.brand;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bugbusters.backend.brand.dto.BrandResponseDTO;
import com.bugbusters.backend.dto.error.ApiErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("api/v1/marcas")
public class BrandController {
    private final BrandService service;

    public BrandController(BrandService service) {
        this.service = service;
    }

    @ApiResponse(responseCode = "200", description = "Marcas recuperadas com sucesso")
    @GetMapping
    public Page<BrandResponseDTO> findAll(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAllBrands(pageable);
    }

    @Operation(summary = "Excluir marca", description = "Remove uma marca, desde que não haja vendas vinculadas.")
    @ApiResponse(responseCode = "204", description = "Marca excluída com sucesso")
    @ApiResponse(responseCode = "404", description = "Marca não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Marca possui vendas vinculadas", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
