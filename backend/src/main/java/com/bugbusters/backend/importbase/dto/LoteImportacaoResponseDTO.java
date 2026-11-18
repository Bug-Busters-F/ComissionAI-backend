package com.bugbusters.backend.importbase.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Detalhes cadastrais, validações e histórico permanente de um lote de importação")
public record LoteImportacaoResponseDTO(
    @Schema(description = "ID do lote (JobExecution)", example = "123")
    Long reportId,

    @Schema(description = "Identificador UUID do lote", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    String idLote,

    @Schema(description = "Nome do arquivo importado", example = "vendas_outubro.xlsx")
    String nomeArquivo,

    @Schema(description = "Tipo de base importada", example = "SALES")
    String tipoBase,

    @Schema(description = "Competência associada no formato YYYY-MM", example = "2026-10", nullable = true)
    String competencia,

    @Schema(description = "Status atual do lote", example = "CONCLUIDO")
    String status,

    @Schema(description = "Total de registros processados", example = "100")
    int totalLinhas,

    @Schema(description = "Mensagem ou motivo de falha/exclusão", nullable = true)
    String mensagem,

    @Schema(description = "Data e hora de criação do lote")
    OffsetDateTime criadoEm,

    @Schema(description = "Data e hora de início do processamento", nullable = true)
    OffsetDateTime iniciadoEm,

    @Schema(description = "Data e hora de término do processamento", nullable = true)
    OffsetDateTime finalizadoEm,

    @Schema(description = "Data e hora de exclusão do lote", nullable = true)
    OffsetDateTime excluidoEm,

    @Schema(description = "Usuário responsável pela exclusão", nullable = true)
    String excluidoPor,

    @Schema(description = "Total de linhas válidas confirmadas", example = "98")
    int linhasValidas,

    @Schema(description = "Indica se o lote foi rejeitado integralmente")
    boolean rejeicaoIntegral,

    @Schema(description = "Hash SHA-256 do conteúdo do arquivo original", nullable = true)
    String hashConteudo,

    @Schema(description = "ID do arquivo original preservado no banco", nullable = true)
    UUID arquivoImportacaoId,

    @Schema(description = "Origem da geração do lote (IMPORTACAO_DIRETA ou PREPARACAO_CAMPANHA)")
    String origemLote,

    @Schema(description = "Usuário responsável pelo envio/criação do lote")
    String criadoPor,

    @Schema(description = "Perfil do usuário responsável pela criação", nullable = true)
    String perfilCriador,

    @Schema(description = "Motivo registrado na exclusão do lote", nullable = true)
    String motivoExclusao,

    @Schema(description = "Perfil do usuário que excluiu o lote", nullable = true)
    String perfilExclusao,

    @Schema(description = "Report ID do lote anterior substituído, quando houver", nullable = true)
    Long envioAnteriorReportId,

    @Schema(description = "Report ID do lote substituto, quando houver", nullable = true)
    Long substituidoPorReportId,

    @Schema(description = "Total de inconsistências/validações persistidas no lote")
    long totalInconsistencias,

    @Schema(description = "Total de validações de severidade IMPEDITIVO")
    long totalImpeditivos,

    @Schema(description = "Total de validações de severidade ADVERTENCIA")
    long totalAdvertencias,

    @Schema(description = "Total de validações de severidade AVISO")
    long totalAvisos,

    @Schema(description = "Validações detalhadas persistidas (retornadas na consulta de detalhe do lote)", nullable = true)
    List<InconsistenciaLoteDTO> inconsistencias
) {
    /**
     * Construtor de compatibilidade com os 13 parâmetros anteriores.
     */
    public LoteImportacaoResponseDTO(
            Long reportId,
            String idLote,
            String nomeArquivo,
            String tipoBase,
            String competencia,
            String status,
            int totalLinhas,
            String mensagem,
            OffsetDateTime criadoEm,
            OffsetDateTime iniciadoEm,
            OffsetDateTime finalizadoEm,
            OffsetDateTime excluidoEm,
            String excluidoPor
    ) {
        this(reportId, idLote, nomeArquivo, tipoBase, competencia, status, totalLinhas, mensagem,
                criadoEm, iniciadoEm, finalizadoEm, excluidoEm, excluidoPor,
                0, false, null, null, null, null, null, null, null, null, null,
                0L, 0L, 0L, 0L, null);
    }
}
