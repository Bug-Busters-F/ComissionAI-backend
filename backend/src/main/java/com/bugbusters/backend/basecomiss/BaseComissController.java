package com.bugbusters.backend.basecomiss;

import com.bugbusters.backend.dto.basecomiss.BaseComissResponseDTO;
import com.bugbusters.backend.dto.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bases-comissao")
@Tag(name = "Bases de Comissão", description = "Consulta e exclusão de taxas contratuais padrão por Marca e Cargo (tb_basecomiss)")
public class BaseComissController {

    private final BaseComissService service;

    public BaseComissController(BaseComissService service) {
        this.service = service;
    }

    @Operation(summary = "Listar bases de comissão", description = "Retorna os percentuais de comissão base de forma paginada com filtros opcionais.")
    @ApiResponse(responseCode = "200", description = "Bases de comissão recuperadas com sucesso")
    @GetMapping
    public Page<BaseComissResponseDTO> listar(
            @Parameter(description = "Número da página, começando em 0", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de itens por página", example = "20") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Código da marca para filtro", example = "10") @RequestParam(required = false) Integer codMarca,
            @Parameter(description = "Código do cargo para filtro", example = "100") @RequestParam(required = false) Integer codCargo,
            @Parameter(description = "Mês de referência (formato YYYY-MM-DD)", example = "2025-12-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mesReferencia) {

        Pageable pageable = PageRequest.of(page, size);
        return service.listar(pageable, codMarca, codCargo, mesReferencia);
    }

    @Operation(summary = "Consultar taxa padrão contratual", description = "Consulta a taxa padrão contratual aplicável para a combinação de Marca e Cargo informada.")
    @ApiResponse(responseCode = "200", description = "Taxa contratual encontrada com sucesso")
    @ApiResponse(responseCode = "404", description = "Taxa contratual não encontrada para os parâmetros informados",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/taxa-padrao")
    public ResponseEntity<BaseComissResponseDTO> consultarTaxaPadrao(
            @Parameter(description = "Código numérico da marca", example = "10", required = true) @RequestParam Integer codMarca,
            @Parameter(description = "Código numérico do cargo", example = "100", required = true) @RequestParam Integer codCargo,
            @Parameter(description = "Mês de referência opcional (busca a competência mais recente se omitido)", example = "2025-12-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mesReferencia) {

        return service.consultarTaxaBase(codMarca, codCargo, mesReferencia)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Excluir todas as taxas base de comissão", description = """
            Remove todas as taxas de comissão base cadastradas. A exclusão é bloqueada caso
            existam cálculos ou logs financeiros vinculados no sistema.
            """)
    @ApiResponse(responseCode = "200", description = "Taxas base excluídas com sucesso")
    @ApiResponse(responseCode = "409", description = "Taxas base possuem cálculos ou logs vinculados",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @DeleteMapping("/todas")
    public ResponseEntity<com.bugbusters.backend.dto.TotalExcluidoResponseDTO> deletarTodas() {
        long total = service.deletarTodas();
        return ResponseEntity.ok(new com.bugbusters.backend.dto.TotalExcluidoResponseDTO(total));
    }

    @Operation(summary = "Excluir taxa base de comissão", description = """
            Remove um registro de taxa de comissão base pelo seu ID (UUID). A exclusão é
            bloqueada caso existam cálculos ou logs financeiros vinculados à marca e cargo
            na mesma competência.
            """)
    @ApiResponse(responseCode = "204", description = "Taxa base excluída com sucesso")
    @ApiResponse(responseCode = "404", description = "Taxa base não encontrada",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Taxa base possui cálculos ou logs vinculados na competência",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID (UUID) da taxa base a ser excluída") @PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
