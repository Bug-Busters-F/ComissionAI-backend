package com.bugbusters.backend.dto.referencia;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dados detalhados de uma venda pertencente ao público-alvo recortado da campanha")
public record VendaRecortadaDTO(
        @Schema(description = "Identificador único da venda (UUID da Sale)", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        UUID id,

        @Schema(description = "Matrícula do colaborador responsável pela venda", example = "MATRIC-1")
        String matricula,

        @Schema(description = "Código numérico do cargo do colaborador", example = "200")
        Integer codCargo,

        @Schema(description = "Descrição textual do cargo", example = "VENDEDOR BALCAO")
        String descriCargo,

        @Schema(description = "Código da loja onde a venda foi realizada", example = "75")
        Integer codLoja,

        @Schema(description = "Código numérico da marca", example = "10")
        Integer codMarca,

        @Schema(description = "Descrição da marca", example = "PRETO")
        String descrMarca,

        @Schema(description = "Canal de venda", example = "ECOMMERCE")
        String canal,

        @Schema(description = "Data em que a venda foi realizada", example = "2026-09-15")
        LocalDate dataVenda,

        @Schema(description = "Valor bruto transacionado na venda", example = "5000.00")
        BigDecimal valorVenda,

        @Schema(description = "Coleção de regras da campanha que contemplam o público desta venda")
        List<RegraAplicavelVendaDTO> regrasCorrespondentes,

        @Schema(description = "Identificador da regra com maior especificidade para esta venda", example = "10")
        Long regraVencedoraId,

        @Schema(description = "Nome da regra vencedora", example = "Regra E-commerce 1K a 5K")
        String regraVencedoraNome,

        @Schema(description = "Indica se o valor da venda atende à faixa de valor da regra vencedora (ou de alguma regra correspondente)", example = "true")
        boolean atendeFaixaValor,

        @Schema(description = "Indica se esta venda pertence ao público de mais de uma regra da campanha", example = "false")
        boolean temSobreposicao,

        @Schema(description = "Indica se houve empate na maior especificidade entre as regras aplicáveis (risco de bloqueio por ambiguidade)", example = "false")
        boolean temEmpateEspecificidade,

        @Schema(description = "Indica se a venda possui todos os vínculos cadastrais e contratuais válidos para cálculo", example = "true")
        boolean valida,

        @Schema(description = "Lista de impedimentos ou inconsistências cadastrais identificados nesta venda")
        List<String> impedimentos
) {}
