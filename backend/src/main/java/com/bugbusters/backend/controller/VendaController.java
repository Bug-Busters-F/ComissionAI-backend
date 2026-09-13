package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.dto.venda.VendaRequestDTO;
import com.bugbusters.backend.dto.venda.VendaResponseDTO;
import com.bugbusters.backend.service.VendaService;
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
public class VendaController {

    private final VendaService vendaService;

    public VendaController(VendaService vendaService) {
        this.vendaService = vendaService;
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
    public ResponseEntity<VendaResponseDTO> registrarVenda(@Valid @RequestBody VendaRequestDTO request) {
        VendaResponseDTO response = vendaService.registrarVenda(request);

        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(uri).body(response);
    }
}
