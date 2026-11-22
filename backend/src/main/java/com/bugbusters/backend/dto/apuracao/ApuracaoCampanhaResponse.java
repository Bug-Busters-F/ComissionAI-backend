package com.bugbusters.backend.dto.apuracao;

import com.bugbusters.backend.dto.calculo.CalculoImpedimentoDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Resposta consolidada da apuração definitiva de comissões por campanha")
public record ApuracaoCampanhaResponse(
        @Schema(description = "Identificador único da apuração")
        UUID apuracaoId,

        @Schema(description = "Protocolo de liquidação financeira")
        UUID protocolo,

        @Schema(description = "ID da campanha")
        Long campanhaId,

        @Schema(description = "ID da versão da campanha")
        Long campanhaVersaoId,

        @Schema(description = "Número da versão da campanha")
        Integer versaoCampanhaNumero,

        @Schema(description = "Competência de apuração no formato YYYY-MM")
        String competencia,

        @Schema(description = "Data inicial considerada")
        LocalDate dataInicio,

        @Schema(description = "Data final considerada")
        LocalDate dataFim,

        @Schema(description = "Versão do programa Python homologado executado")
        Integer versaoProgramaPython,

        @Schema(description = "Checksum SHA-256 do programa Python")
        String checksumPython,

        @Schema(description = "Indica se o cálculo foi executado com o programa Python homologado")
        boolean executadoViaPython,

        @Schema(description = "Quantidade total de vendas processadas da campanha")
        int totalVendasProcessadas,

        @Schema(description = "Quantidade de vendas calculadas com sucesso")
        int totalCalculados,

        @Schema(description = "Quantidade de vendas com impedimentos cadastrais")
        int totalImpedimentos,

        @Schema(description = "Valor total das vendas calculadas com sucesso")
        BigDecimal valorTotalVendas,

        @Schema(description = "Valor total da comissão gerada")
        BigDecimal valorTotalComissao,

        @Schema(description = "Valor total de bônus gerado")
        BigDecimal valorTotalBonus,

        @Schema(description = "Usuário executor da apuração")
        String executadoPor,

        @Schema(description = "Data e hora de conclusão da apuração")
        OffsetDateTime executadoEm,

        @Schema(description = "Status da apuração (CONCLUIDO, FALHA, etc.)")
        String status,

        @Schema(description = "Observações registradas")
        String observacoes,

        @Schema(description = "Vendas apuradas e liquidadas com sucesso")
        List<ItemApuracaoResponse> itens,

        @Schema(description = "Vendas com impedimentos durante a apuração")
        List<CalculoImpedimentoDTO> impedimentos
) {}

