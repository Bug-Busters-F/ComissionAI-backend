package com.bugbusters.backend.dto.cobertura;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Aviso não-bloqueante de sobreposição de vendas com mesma chave (matrícula, data, valor) entre lotes da competência")
public record AvisoSobreposicaoVendaDTO(
        @Schema(description = "Matrícula do colaborador", example = "102030")
        String matricula,

        @Schema(description = "Data da venda", example = "2026-07-25")
        LocalDate dataVenda,

        @Schema(description = "Valor da venda", example = "1500.00")
        BigDecimal valorVenda,

        @Schema(description = "Quantidade de ocorrências encontradas", example = "2")
        int quantidadeOcorrencias,

        @Schema(description = "Report IDs dos lotes envolvidos na sobreposição", example = "[10020, 10025]")
        List<Long> reportIdsEnvolvidos
) {
}
