package com.bugbusters.backend.dto.simulacao;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Comparativo detalhado por venda em todos os cenários de simulação")
public record VendaSimuladaDetalheDTO(
        @Schema(description = "Identificador único da venda (UUID da Sale)")
        UUID id,

        @Schema(description = "Matrícula do vendedor", example = "MATRIC-12")
        String matricula,

        @Schema(description = "Código do cargo", example = "100")
        Integer codCargo,

        @Schema(description = "Descrição do cargo", example = "VENDEDOR LOJA")
        String descriCargo,

        @Schema(description = "Código da loja", example = "5")
        Integer codLoja,

        @Schema(description = "Código da marca", example = "10")
        Integer codMarca,

        @Schema(description = "Descrição da marca", example = "PRETO")
        String descrMarca,

        @Schema(description = "Canal de venda", example = "ECOMMERCE")
        String canal,

        @Schema(description = "Data histórica da venda", example = "2026-09-12")
        LocalDate dataVenda,

        @Schema(description = "Valor histórico original da venda", example = "5000.00")
        BigDecimal valorVendaOriginal,

        @Schema(description = "Taxa base contratual (tb_basecomiss)", example = "0.0300")
        BigDecimal taxaBaseContratual,

        @Schema(description = "Valor da comissão calculada pela base contratual no cenário 100%", example = "150.00")
        BigDecimal valorComissaoBase,

        @Schema(description = "Resultado no cenário 100% (proposta revisada)")
        VendaSimuladaCenarioItemDTO cenario100,

        @Schema(description = "Resultado no cenário de estresse 80%")
        VendaSimuladaCenarioItemDTO cenario80,

        @Schema(description = "Resultado no cenário otimista 120%")
        VendaSimuladaCenarioItemDTO cenario120,

        @Schema(description = "Resultado no cenário de alta performance 150%")
        VendaSimuladaCenarioItemDTO cenario150,

        @Schema(description = "Diferença financeira no cenário de 100% (Proposta - Base)", example = "100.00")
        BigDecimal deltaComissao100,

        @Schema(description = "Indica se a venda possui vínculos válidos para simulação", example = "true")
        boolean valida,

        @Schema(description = "Lista de impedimentos ou alertas identificados na venda")
        List<String> impedimentos
) {}
