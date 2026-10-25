package com.bugbusters.backend.dto.fechamento;

import com.bugbusters.backend.dto.calculo.CalculoImpedimentoDTO;
import com.bugbusters.backend.dto.calculo.CalculoItemResponseDTO;
import com.bugbusters.backend.model.FechamentoCompetencia;
import com.bugbusters.backend.model.StatusFechamento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Resultado consolidado do Fechamento Final de Competência (BACK-11)")
public record FechamentoResponseDTO(
        @Schema(description = "Identificador único do registro de fechamento (UUID)")
        UUID id,

        @Schema(description = "Protocolo de auditoria da execução")
        UUID protocolo,

        @Schema(description = "Competência apurada (mês/ano)", example = "2026-10")
        String competencia,

        @Schema(description = "Estado do fechamento final", example = "CONCLUIDO")
        StatusFechamento status,

        @Schema(description = "Tipo de liquidação", example = "FINAL")
        String tipo,

        @Schema(description = "Total de vendas da competência processadas", example = "100")
        int totalVendasProcessadas,

        @Schema(description = "Total de vendas calculadas com sucesso", example = "98")
        int totalCalculados,

        @Schema(description = "Total de vendas com impedimento", example = "2")
        int totalImpedimentos,

        @Schema(description = "Soma do valor bruto das vendas calculadas com sucesso (R$)", example = "150000.00")
        BigDecimal valorTotalVendas,

        @Schema(description = "Soma do valor total de comissões apuradas (R$)", example = "7500.00")
        BigDecimal valorTotalComissao,

        @Schema(description = "Responsável pela execução do fechamento", example = "controladoria@empresa.com")
        String executadoPor,

        @Schema(description = "Momento de início do fechamento")
        OffsetDateTime iniciadoEm,

        @Schema(description = "Momento de conclusão do fechamento")
        OffsetDateTime concluidoEm,

        @Schema(description = "Justificativa informada no fechamento")
        String motivo,

        @Schema(description = "Mensagem descritiva de status")
        String mensagem,

        @Schema(description = "Relação de itens apurados com sucesso")
        List<CalculoItemResponseDTO> resultados,

        @Schema(description = "Relação de impedimentos detectados")
        List<CalculoImpedimentoDTO> impedimentos
) {
    public static FechamentoResponseDTO resumido(FechamentoCompetencia f) {
        return new FechamentoResponseDTO(
                f.getId(),
                f.getProtocolo(),
                f.getCompetencia(),
                f.getStatus(),
                f.getTipo(),
                f.getTotalVendasProcessadas(),
                f.getTotalCalculados(),
                f.getTotalImpedimentos(),
                f.getValorTotalVendas(),
                f.getValorTotalComissao(),
                f.getExecutadoPor(),
                f.getIniciadoEm(),
                f.getConcluidoEm(),
                f.getMotivo(),
                f.getStatus() == StatusFechamento.CONCLUIDO ? "Fechamento concluído com sucesso." : (f.getStatus() == StatusFechamento.FALHOU ? f.getMotivoFalha() : "Em processamento."),
                java.util.Collections.emptyList(),
                java.util.Collections.emptyList()
        );
    }
}
