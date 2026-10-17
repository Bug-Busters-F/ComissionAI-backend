package com.bugbusters.backend.dto.basecomiss;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Dados da taxa de comissão contratual base cadastrada")
public record BaseComissResponseDTO(
        @Schema(description = "Identificador único do registro de comissão base")
        UUID id,

        @Schema(description = "Código numérico da marca", example = "10")
        Integer codMarca,

        @Schema(description = "Descrição/nome da marca", example = "PRETO")
        String descrMarca,

        @Schema(description = "Código numérico do cargo", example = "100")
        Integer codCargo,

        @Schema(description = "Descrição/função do cargo", example = "VENDEDOR LOJA")
        String descriCargo,

        @Schema(description = "Percentual padrão de comissão (escala decimal, ex: 0.0250 = 2.50%)", example = "0.0250")
        BigDecimal percentual,

        @Schema(description = "Mês de referência da competência contratual", example = "2025-12-01")
        LocalDate mesReferencia
) {
}
