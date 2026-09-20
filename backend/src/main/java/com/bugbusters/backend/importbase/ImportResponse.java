package com.bugbusters.backend.importbase;

import java.util.List;

// import java.time.OffsetDateTime;
// import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Relatório de validação e processamento de upload de planilhas/CSVs")
public record ImportResponse(
    @Schema(description = "Nome do arquivo submetido", example = "BASE_VENDAS_OUT25.csv")
    String nomeArquivo,

    @Schema(description = "Tipo de base", example = "VENDAS")
    ImportType tipoBase,

    // TODO: remover esse atributo depois
    @Schema(description = "Linhas lidas")
    List<?> linhas

    // TODO: VALIDAR ESSES CAMPOS PARA A IMPLEMENTAÇÃO DO SPRING BATCH
    // @Schema(description = "Status consolidado: SUCESSO, REJEITADO, PROCESSADO_COM_AVISOS", example = "REJEITADO")
    // String status,

    // @Schema(description = "Total de linhas no arquivo", example = "1500")
    // int totalLinhas,

    // @Schema(description = "Total de linhas válidas", example = "1498")
    // int linhasValidas,

    // @Schema(description = "Indica se houve rejeição integral por falha impeditiva", example = "true")
    // boolean rejeicaoIntegral

    // @Schema(description = "Lista detalhada de inconsistencias apontando linha, campo e gravidade")
    // List<ItemInconsistenciaDTO> inconsistencias,

    // OffsetDateTime processadoEm
) {
    public record ItemInconsistenciaDTO(
        @Schema(description = "Número da linha no arquivo", example = "42")
        int linha,

        @Schema(description = "Coluna com inconsistência", example = "canal")
        String campo,

        @Schema(description = "Motivo da falha", example = "Canal não reconhecido ou em branco.")
        String motivo,

        @Schema(description = "Severidade da falha", example = "IMPEDITIVO")
        ImportInconsistencySeverity severidade
    ) {}
}
