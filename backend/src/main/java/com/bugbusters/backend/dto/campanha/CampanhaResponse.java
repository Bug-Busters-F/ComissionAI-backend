package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.EstadoCampanha;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

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
        OffsetDateTime atualizadoEm
) {
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
            StatusRegra status
    ) {}
}