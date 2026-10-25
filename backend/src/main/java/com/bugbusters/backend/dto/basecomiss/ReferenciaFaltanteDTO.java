package com.bugbusters.backend.dto.basecomiss;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Identificação de marca e cargo sem cobertura de taxa base contratual na competência")
public record ReferenciaFaltanteDTO(
        @Schema(description = "Código da marca", example = "10")
        Integer codMarca,

        @Schema(description = "Descrição da marca", example = "VERMELHO")
        String descrMarca,

        @Schema(description = "Código do cargo", example = "100")
        Integer codCargo,

        @Schema(description = "Descrição do cargo", example = "VENDEDOR LOJA")
        String descriCargo,

        @Schema(description = "Competência exigida (YYYY-MM)", example = "2026-10")
        String competencia
) {}
