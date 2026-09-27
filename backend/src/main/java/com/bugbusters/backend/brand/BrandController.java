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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/marcas")
@Tag(name = "6. Marcas", description = "Consulta e exclusão de marcas, criadas a partir da importação da base de Vendas")
public class BrandController {
    private final BrandService service;

    public BrandController(BrandService service) {
        this.service = service;
    }

    @Operation(summary = "Listar marcas", description = "Retorna as marcas cadastradas de forma paginada.")
    @ApiResponse(responseCode = "200", description = "Marcas recuperadas com sucesso")
    @GetMapping
    public Page<BrandResponseDTO> findAll(
            @Parameter(description = "Número da página, começando em 0", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de itens por página (mínimo 1)", example = "20") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return service.findAllBrands(pageable);
    }

    @Operation(summary = "Excluir marca", description = """
            Remove uma marca pelo seu ID. A exclusão é bloqueada caso existam vendas
            vinculadas à marca.
            """)
    @ApiResponse(responseCode = "204", description = "Marca excluída com sucesso")
    @ApiResponse(responseCode = "404", description = "Marca não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Marca possui vendas vinculadas", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID (UUID) da marca a ser excluída") @PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}