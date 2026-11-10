package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.OrigemOrcamentoCampanha;
import com.bugbusters.backend.model.StatusAceiteSimulacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Representação detalhada da campanha e da coleção de regras vinculadas")
public record CampanhaResponse(
        Long id,
        String titulo,
        String textoOriginal,
        EstadoCampanha estado,
        LocalDate dataInicio,
        LocalDate dataFim,
        List<RegraVinculadaDTO> regras,
        Boolean possuiIncompletas,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        UUID simulacaoAceitaId,
        StatusAceiteSimulacao statusAceite,
        Integer versaoVigente,
        Integer versaoProposta,
        Integer ultimaEtapa,
        Integer etapaMaisAvancada,
        Long revisaoControle,
        Integer versaoOrigem,
        String motivoBloqueioEdicao,

        @Schema(description = "Teto orçamentário monetário da campanha (BACK-8)", example = "50000.00")
        BigDecimal orcamento,

        @Schema(description = "Orçamento monetário extraído pela IA", example = "45000.00")
        BigDecimal orcamentoExtraidoIa,

        @Schema(description = "Origem do orçamento cadastrado", example = "CONFIRMADO_USUARIO")
        OrigemOrcamentoCampanha origemOrcamento,

        @Schema(description = "Indica se o orçamento foi confirmado explicitamente", example = "true")
        Boolean orcamentoConfirmado,

        @Schema(description = "Responsável pela confirmação do orçamento", example = "analista.comissoes@empresa.com")
        String orcamentoConfirmadoPor,

        @Schema(description = "Data e hora da confirmação do orçamento")
        OffsetDateTime orcamentoConfirmadoEm,

        @Schema(description = "Justificativa informada na confirmação/alteração do orçamento")
        String justificativaOrcamento,

        @Schema(description = "Indica se a campanha foi encaminhada ao gerente para aprovação", example = "false")
        Boolean encaminhadaGerente,

        @Schema(description = "Responsável pelo encaminhamento ao gerente")
        String encaminhadaGerentePor,

        @Schema(description = "Data e hora do encaminhamento ao gerente")
        OffsetDateTime encaminhadaGerenteEm,

        @Schema(description = "Observação de encaminhamento ao gerente")
        String observacaoEncaminhamento
) {
    public CampanhaResponse(
            Long id,
            String titulo,
            String textoOriginal,
            EstadoCampanha estado,
            LocalDate dataInicio,
            LocalDate dataFim,
            List<RegraVinculadaDTO> regras,
            Boolean possuiIncompletas,
            OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm
    ) {
        this(id, titulo, textoOriginal, estado, dataInicio, dataFim, regras, possuiIncompletas, criadoEm, atualizadoEm,
                null, null, null, null, 1, 1, null, null, null,
                null, null, null, false, null, null, null, false, null, null, null);
    }

    public CampanhaResponse(
            Long id,
            String titulo,
            String textoOriginal,
            EstadoCampanha estado,
            LocalDate dataInicio,
            LocalDate dataFim,
            List<RegraVinculadaDTO> regras,
            Boolean possuiIncompletas,
            OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm,
            UUID simulacaoAceitaId,
            StatusAceiteSimulacao statusAceite,
            Integer versaoVigente,
            Integer versaoProposta,
            Integer ultimaEtapa,
            Integer etapaMaisAvancada,
            Long revisaoControle,
            Integer versaoOrigem,
            String motivoBloqueioEdicao
    ) {
        this(id, titulo, textoOriginal, estado, dataInicio, dataFim, regras, possuiIncompletas, criadoEm, atualizadoEm,
                simulacaoAceitaId, statusAceite, versaoVigente, versaoProposta, ultimaEtapa, etapaMaisAvancada, revisaoControle, versaoOrigem, motivoBloqueioEdicao,
                null, null, null, false, null, null, null, false, null, null, null);
    }

    public record RegraVinculadaDTO(
            Long id,
            String blocoId,
            String trechoOrigem,
            String nome,
            String canal,
            Integer codMarca,
            String descrMarca,
            Integer codLoja,
            Integer codCargo,
            String descriCargo,
            String matricula,
            BigDecimal valorMinimo,
            Boolean minInclusivo,
            BigDecimal valorMaximo,
            Boolean maxInclusivo,
            TipoOperacaoBase tipoOperacao,
            BigDecimal valorAjuste,
            String tipoBaseReferencia,
            BigDecimal taxaBaseConsultada,
            String descricaoReferencia,
            BigDecimal taxa,
            String explicacao,
            String pythonEquivalente,
            String pendencias,
            Boolean completa,
            LocalDate dataInicio,
            LocalDate dataFim,
            StatusRegra status,
            BigDecimal bonusPercentual,
            BigDecimal bonusValorFixo,
            RastreabilidadeRegraDTO rastreabilidade
    ) {}
}
