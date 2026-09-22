package com.bugbusters.backend.sales;

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.sales.dto.SaleRequestDTO;
import com.bugbusters.backend.sales.dto.SaleResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/vendas")
@Tag(name = "3. Vendas Individuais", description = "Registro de vendas individuais para posterior cálculo de comissionamento")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService salesService) {
        this.saleService = salesService;
    }

    @Operation(
            summary = "Registrar venda individual",
            description = """
                    Recebe o payload de uma venda individual, valida os campos obrigatórios e persiste
                    no banco de dados. O campo `idVendaExterno` deve ser único por venda e serve de
                    garantia de idempotência para a etapa de cálculo de comissão.
                    O valor da venda é tratado como BigDecimal para precisão financeira.
                    """
    )
    @ApiResponse(responseCode = "201", description = "Venda registrada com sucesso")
    @ApiResponse(
            responseCode = "400",
            description = "Dados inválidos (campos obrigatórios ausentes, valor não positivo ou ID externo duplicado)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @PostMapping
    public ResponseEntity<SaleResponseDTO> registrarVenda(@Valid @RequestBody SaleRequestDTO request) {
        SaleResponseDTO response = saleService.registrarVenda(request);

        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(uri).body(response);
    }
}
