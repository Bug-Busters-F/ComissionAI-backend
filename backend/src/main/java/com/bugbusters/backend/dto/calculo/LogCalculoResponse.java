package com.bugbusters.backend.dto.calculo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.bugbusters.backend.model.LogCalculoImutavel;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Log imutável de auditoria de cálculo financeiro")
public record LogCalculoResponse(
    @Schema(description = "Identificador único do log", example = "7b2e652a-9941-4770-9852-51322ab5e1f0")
    UUID idLog,

    @Schema(description = "Protocolo de auditoria gerado pelo motor de cálculo", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID protocolo,

    @Schema(description = "Identificador único da venda auditada (UUID da venda)", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    UUID idVenda,

    @Schema(description = "Matrícula do colaborador", example = "MATRIC-1")
    String matricula,

    @Schema(description = "Código do cargo histórico", example = "100")
    Integer codCargo,

    @Schema(description = "Código da loja", example = "75")
    Integer codLoja,

    @Schema(description = "Código da marca", example = "10")
    Integer codMarca,

    @Schema(description = "Valor original transacionado", example = "1000.00")
    BigDecimal valorOriginal,

    @Schema(description = "Valor bruto da venda", example = "1000.00")
    BigDecimal valorVenda,

    @Schema(description = "Taxa percentual aplicada", example = "0.1000")
    BigDecimal taxaAplicada,

    @Schema(description = "Valor da comissão calculada", example = "100.00")
    BigDecimal valorComissao,

    @Schema(description = "ID da regra aplicada", example = "1")
    Long idRegra,

    @Schema(description = "Data histórica da venda", example = "2025-12-01")
    LocalDate dataVenda,

    @Schema(description = "Canal de venda", example = "ECOMMERCE")
    String canal,

    @Schema(description = "Origem da execução do cálculo", example = "MOTOR_PRODUCAO")
    String origemExecucao,

    @Schema(description = "Usuário ou sistema executor", example = "SISTEMA")
    String usuarioExecutor,

    @Schema(description = "Data e hora de execução", example = "2026-09-14T09:30:00Z")
    OffsetDateTime executadoEm
) {

    /**
     * Construtor de compatibilidade para código ou testes que utilizam a assinatura simplificada prévia.
     */
    public LogCalculoResponse(UUID idLog, String matricula, Long idRegra, BigDecimal valorOriginal, BigDecimal taxaAplicada, BigDecimal valorComissao, OffsetDateTime executadoEm) {
        this(idLog, null, null, matricula, null, null, null, valorOriginal, valorOriginal, taxaAplicada, valorComissao, idRegra, null, null, null, null, executadoEm);
    }

    /**
     * Constrói o DTO completo preservando todos os valores históricos e referências da entidade imutável de log.
     */
    public static LogCalculoResponse fromEntity(LogCalculoImutavel log) {
        if (log == null) {
            return null;
        }
        return new LogCalculoResponse(
                log.getId(),
                log.getProtocolo(),
                log.getIdVenda(),
                log.getMatricula(),
                log.getCodCargo(),
                log.getCodLoja(),
                log.getCodMarca(),
                log.getValorVenda(),
                log.getValorVenda(),
                log.getTaxaAplicada(),
                log.getValorComissao(),
                log.getIdRegra(),
                log.getDataVenda(),
                log.getCanal(),
                log.getOrigemExecucao(),
                log.getUsuarioExecutor(),
                log.getExecutadoEm()
        );
    }
}
