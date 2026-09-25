package com.bugbusters.backend.store;

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
import com.bugbusters.backend.store.dto.StoreResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/lojas")
@Tag(name = "5. Lojas", description = "Consulta e exclusão de lojas, criadas a partir da importação das bases de RH e Vendas")
public class StoreController {
    private final StoreService service;

    public StoreController(StoreService service) {
        this.service = service;
    }

    @Operation(summary = "Listar lojas", description = "Retorna as lojas cadastradas de forma paginada.")
    @ApiResponse(responseCode = "200", description = "Lojas recuperadas com sucesso")
    @GetMapping
    public Page<StoreResponseDTO> findAll(
            @Parameter(description = "Número da página, começando em 0", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de itens por página (mínimo 1)", example = "20") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    @Operation(summary = "Excluir loja", description = """
            Remove uma loja pelo seu ID. A exclusão é bloqueada caso existam vendas ou
            matrículas vinculadas à loja.
            """)
    @ApiResponse(responseCode = "204", description = "Loja excluída com sucesso")
    @ApiResponse(responseCode = "404", description = "Loja não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Loja possui vendas ou matrículas vinculadas", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID (UUID) da loja a ser excluída") @PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}