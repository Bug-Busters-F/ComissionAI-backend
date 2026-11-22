package com.bugbusters.backend.dto.auditoria;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Resumo relacional de lote de importação e arquivo preservado vinculados a um evento ou cálculo.
 */
@Schema(description = "Relacionamento do evento com lote de importação e arquivo preservado")
public record RelacionamentoArquivoResumoDTO(
        Long envioArquivoId,
        Long reportId,
        String idLote,
        UUID arquivoImportacaoId,
        UUID lotePreparacaoId,
        String nomeArquivo,
        String tipoBase,
        String competencia,
        String status,
        Integer totalLinhas,
        Integer linhasValidas,
        String criadoPor,
        Boolean excluido
) {}
