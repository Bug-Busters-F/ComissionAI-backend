package com.bugbusters.backend.position;

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

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.position.dto.PositionResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/cargos")
@Tag(name = "7. Cargos", description = "Consulta e exclusão de cargos, criados a partir da importação da base de RH")
public class PositionController {

    private final PositionService service;

    public PositionController(PositionService service) {
        this.service = service;
    }

    @Operation(summary = "Listar cargos", description = "Retorna os cargos cadastrados de forma paginada.")
    @ApiResponse(responseCode = "200", description = "Cargos recuperados com sucesso")
    @GetMapping
    public Page<PositionResponseDTO> findAllPositions(
            @Parameter(description = "Número da página, começando em 0", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de itens por página (mínimo 1)", example = "20") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    @Operation(summary = "Excluir cargo", description = """
            Remove um cargo pelo seu ID. A exclusão é bloqueada caso existam matrículas
            vinculadas ao cargo.
            """)
    @ApiResponse(responseCode = "204", description = "Cargo excluído com sucesso")
    @ApiResponse(responseCode = "404", description = "Cargo não encontrado", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Cargo possui matrículas vinculadas", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID (UUID) do cargo a ser excluído") @PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}