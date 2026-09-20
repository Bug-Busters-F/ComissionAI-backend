package com.bugbusters.backend.dto.calculo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.bugbusters.backend.model.Marca;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Entrada para apuração de comissão sobre uma venda")
public record CalculoComissaoRequest(
    @Schema(description = "Identificador único da venda (UUID)", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    UUID idVenda,

    @Schema(description = "Matrícula cadastral do colaborador (chave de vínculo com o RH)", example = "MATRIC-1")
    @NotBlank(message = "A matrícula é obrigatória")
    String matricula,

    @Schema(description = "Valor bruto transacionado", example = "1000.00")
    @NotNull(message = "O valor da venda é obrigatório")
    @Positive(message = "O valor da venda deve ser positivo")
    BigDecimal valorVenda,

    @Schema(description = "Data de ocorrência da venda", example = "2025-12-01")
    @NotNull(message = "A data da venda é obrigatória")
    LocalDate dataVenda,

    @Schema(description = "Código da marca da venda", example = "10")
    Integer codMarca,

    @Schema(description = "Descrição/cor da marca confidencial (ex: Vermelho, PRETO)", example = "VERMELHO")
    String descrMarca,

    @Schema(description = "Código da loja da venda", example = "75")
    Integer codLoja,

    @Schema(description = "Canal onde a venda ocorreu (fallback padrão: PADRAO)", example = "LOJA_FISICA", defaultValue = "PADRAO")
    String canal
) {
    public CalculoComissaoRequest {
        if (canal == null || canal.isBlank()) {
            canal = "PADRAO";
        }
        if (descrMarca != null && !descrMarca.isBlank()) {
            descrMarca = Marca.padronizar(descrMarca);
            if (codMarca == null) {
                codMarca = Marca.buscarPorNome(descrMarca).map(Marca::getCodigo).orElse(null);
            }
        } else if (codMarca != null) {
            descrMarca = Marca.buscarPorCodigo(codMarca).map(Marca::getDescricao).orElse(null);
        }
    }

    public CalculoComissaoRequest(String matricula, BigDecimal valorVenda, LocalDate dataVenda, Integer codMarca, Integer codLoja, String canal) {
        this(null, matricula, valorVenda, dataVenda, codMarca, null, codLoja, canal);
    }

    public CalculoComissaoRequest(String matricula, BigDecimal valorVenda, LocalDate dataVenda, Integer codMarca, String descrMarca, Integer codLoja, String canal) {
        this(null, matricula, valorVenda, dataVenda, codMarca, descrMarca, codLoja, canal);
    }

    public CalculoComissaoRequest(UUID idVenda, String matricula, BigDecimal valorVenda, LocalDate dataVenda, Integer codMarca, Integer codLoja, String canal) {
        this(idVenda, matricula, valorVenda, dataVenda, codMarca, null, codLoja, canal);
    }
}