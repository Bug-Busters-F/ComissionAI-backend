package com.bugbusters.backend.sales;

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.sales.dto.SaleRequestDTO;
import com.bugbusters.backend.sales.dto.SaleResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vendas")
@Tag(name = "3. Vendas Individuais", description = "Registro, consulta e exclusão de vendas individuais para posterior cálculo de comissionamento")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService salesService) {
        this.saleService = salesService;
    }

    @Operation(summary = "Registrar venda individual", description = """
            Recebe o payload de uma venda individual, valida os campos obrigatórios e persiste
            no banco de dados. O campo `id` é opcional e, quando informado, serve de garantia
            de idempotência: reenviar a mesma venda com dados idênticos retorna o registro
            existente, enquanto dados divergentes para o mesmo ID são rejeitados.
            O valor da venda é tratado como BigDecimal para precisão financeira.
            """)
    @ApiResponse(responseCode = "201", description = "Venda registrada com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos (campos obrigatórios ausentes, valor não positivo ou ID duplicado com dados divergentes)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping
    public ResponseEntity<SaleResponseDTO> registrarVenda(@Valid @RequestBody SaleRequestDTO request) {
        SaleResponseDTO response = saleService.registrarVenda(request);

        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(uri).body(response);
    }

    @Operation(summary = "Listar vendas", description = """
            Retorna as vendas registradas de forma paginada, incluindo matrícula, marca,
            loja, data, valor e canal de venda.
            """)
    @ApiResponse(responseCode = "200", description = "Vendas recuperadas com sucesso")
    @GetMapping
    public Page<SaleResponseDTO> findAllSales(
            @Parameter(description = "Número da página, começando em 0", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de itens por página (mínimo 1)", example = "20") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return saleService.readAllSales(pageable);
    }

    @Operation(summary = "Excluir venda", description = "Remove uma venda individual pelo seu ID.")
    @ApiResponse(responseCode = "204", description = "Venda excluída com sucesso")
    @ApiResponse(responseCode = "404", description = "Venda não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarVenda(
            @Parameter(description = "ID (UUID) da venda a ser excluída") @PathVariable UUID id) {
        saleService.deletarVenda(id);
        return ResponseEntity.noContent().build();
    }
}