package com.bugbusters.backend.registration;

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
import com.bugbusters.backend.registration.dto.RegistrationResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/matriculas")
@Tag(name = "8. Matrículas", description = "Consulta e exclusão de matrículas de colaboradores, criadas a partir da importação da base de RH")
public class RegistrationController {
    private final RegistrationService service;

    public RegistrationController(RegistrationService service) {
        this.service = service;
    }

    @Operation(summary = "Listar matrículas", description = """
            Retorna as matrículas de colaboradores de forma paginada, incluindo cargo, loja,
            data de admissão e data de demissão (quando houver).
            """)
    @ApiResponse(responseCode = "200", description = "Matrículas recuperadas com sucesso")
    @GetMapping
    public Page<RegistrationResponseDTO> findAll(
            @Parameter(description = "Número da página, começando em 0", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de itens por página (mínimo 1)", example = "20") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    @Operation(summary = "Excluir matrícula", description = """
            Remove uma matrícula pelo seu ID. A exclusão é bloqueada caso existam vendas
            vinculadas à matrícula, para preservar o histórico de comissionamento.
            """)
    @ApiResponse(responseCode = "204", description = "Matrícula excluída com sucesso")
    @ApiResponse(responseCode = "404", description = "Matrícula não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Matrícula possui vendas vinculadas", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID (UUID) da matrícula a ser excluída") @PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}