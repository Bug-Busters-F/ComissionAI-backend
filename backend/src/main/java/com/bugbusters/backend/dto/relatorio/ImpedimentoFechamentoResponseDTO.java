package com.bugbusters.backend.dto.relatorio;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Item de impedimento persistido durante o fechamento da competência (FRONT-11 / BACK-15)")
public record ImpedimentoFechamentoResponseDTO(
        @Schema(description = "Identificador único do impedimento")
        UUID id,

        @Schema(description = "Identificador da venda impedida")
        UUID idVenda,

        @Schema(description = "Matrícula do colaborador associada à venda")
        String matricula,

        @Schema(description = "Identificação amigável do colaborador")
        String nomeColaborador,

        @Schema(description = "Data de ocorrência da venda")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate dataVenda,

        @Schema(description = "Valor bruto da venda (R$)")
        BigDecimal valorVenda,

        @Schema(description = "Descrição detalhada do impedimento")
        String motivo,

        @Schema(description = "Classificação padronizada do impedimento", example = "BASE_COMISS_AUSENTE")
        String tipoImpedimento
) {}
