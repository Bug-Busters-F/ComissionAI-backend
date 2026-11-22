package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.model.EstadoCampanha;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Dados para cadastro ou edição de campanha com coleção de regras vinculadas e orçamento")
public record CampanhaRequest(
        @Schema(description = "Título descritivo da campanha", example = "Campanha Black Friday 2026")
        @NotBlank(message = "O título da campanha é obrigatório")
        String titulo,

        @Schema(description = "Texto original em linguagem natural que originou as propostas", example = "1. Pagar 5% no ecommerce em outubro. 2. Loja 75 pagar taxa base + 1.5%")
        @NotBlank(message = "O texto original da campanha é obrigatório")
        String textoOriginal,

        @Schema(description = "Data de início da vigência da campanha. Se omitida, assume a data atual.", example = "2026-10-01")
        LocalDate dataInicio,

        @Schema(description = "Data final da vigência da campanha. Se omitida, aplica-se automaticamente 30 dias contados da data de início.", example = "2026-10-31")
        LocalDate dataFim,

        @Schema(description = "Estado desejado para a campanha (DRAFT por padrão na criação)", example = "DRAFT")
        EstadoCampanha estado,

        @Schema(description = "Coleção de regras vinculadas à campanha")
        @NotEmpty(message = "A campanha deve conter pelo menos uma regra vinculada")
        List<@Valid RegraItemRequest> regras,

        @Schema(description = "Identificador opcional da simulação aceita a vincular", example = "550e8400-e29b-41d4-a716-446655440000")
        java.util.UUID simulacaoAceitaId,

        @Schema(description = "Teto orçamentário monetário da campanha (BACK-8)", example = "50000.00")
        @DecimalMin(value = "0.01", message = "O orçamento deve ser maior que zero")
        BigDecimal orcamento,

        @Schema(description = "Orçamento monetário extraído pela IA (se aplicável)", example = "45000.00")
        BigDecimal orcamentoExtraidoIa,

        @Schema(description = "Indica se o orçamento informado já foi confirmado explicitamente", example = "true")
        Boolean orcamentoConfirmado,

        @Schema(description = "Responsável pela confirmação/alteração do orçamento", example = "analista.comissoes@empresa.com")
        String orcamentoConfirmadoPor,

        @Schema(description = "Justificativa opcional para o orçamento fixado", example = "Ajuste orçamentário aprovado")
        String justificativaOrcamento
) {
    public CampanhaRequest(String titulo, String textoOriginal, LocalDate dataInicio, LocalDate dataFim, List<RegraItemRequest> regras) {
        this(titulo, textoOriginal, dataInicio, dataFim, EstadoCampanha.DRAFT, regras, null, null, null, null, null, null);
    }

    public CampanhaRequest(String titulo, String textoOriginal, LocalDate dataInicio, LocalDate dataFim, EstadoCampanha estado, List<RegraItemRequest> regras) {
        this(titulo, textoOriginal, dataInicio, dataFim, estado, regras, null, null, null, null, null, null);
    }

    public CampanhaRequest(String titulo, String textoOriginal, LocalDate dataInicio, LocalDate dataFim, EstadoCampanha estado, List<RegraItemRequest> regras, java.util.UUID simulacaoAceitaId) {
        this(titulo, textoOriginal, dataInicio, dataFim, estado, regras, simulacaoAceitaId, null, null, null, null, null);
    }

    public CampanhaRequest(String titulo, String textoOriginal, LocalDate dataInicio, LocalDate dataFim, EstadoCampanha estado, List<RegraItemRequest> regras, BigDecimal orcamento, BigDecimal orcamentoExtraidoIa, Boolean orcamentoConfirmado, String orcamentoConfirmadoPor, String justificativaOrcamento) {
        this(titulo, textoOriginal, dataInicio, dataFim, estado, regras, null, orcamento, orcamentoExtraidoIa, orcamentoConfirmado, orcamentoConfirmadoPor, justificativaOrcamento);
    }
}
