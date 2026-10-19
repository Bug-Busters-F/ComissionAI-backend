package com.bugbusters.backend.dto.simulacao;

import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.StatusAceiteSimulacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Resultado da conferência prévia entre a proposta atual da campanha e a simulação aceita")
public record ConferenciaSimulacaoResponse(
        @Schema(description = "Identificador da campanha avaliada")
        Long campanhaId,

        @Schema(description = "Título da campanha")
        String tituloCampanha,

        @Schema(description = "Estado atual da campanha no banco de dados")
        EstadoCampanha estadoCampanha,

        @Schema(description = "Indica se a campanha atende integralmente a todos os critérios e está apta para ativação em produção")
        boolean aptaParaAtivacao,

        @Schema(description = "Status do aceite da simulação vinculada")
        StatusAceiteSimulacao statusAceite,

        @Schema(description = "Identificador da simulação aceita vinculada")
        UUID simulacaoId,

        @Schema(description = "Protocolo da simulação aceita vinculada")
        UUID protocoloSimulacao,

        @Schema(description = "Data e hora do registro de aceite")
        OffsetDateTime dataAceite,

        @Schema(description = "Responsável pelo aceite")
        String aceitoPor,

        @Schema(description = "Hash SHA-256 da proposta congelada no momento do aceite da simulação")
        String hashSimulacao,

        @Schema(description = "Hash SHA-256 da proposta atual da campanha no banco")
        String hashCampanhaAtual,

        @Schema(description = "Lista detalhada de divergências detectadas entre a campanha atual e a simulação aceita (regras, datas, premissas)")
        List<String> divergencias,

        @Schema(description = "Mensagem conclusiva da análise de conformidade")
        String mensagem
) {}
