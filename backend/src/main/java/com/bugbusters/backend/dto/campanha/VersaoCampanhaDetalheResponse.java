package com.bugbusters.backend.dto.campanha;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Snapshot imutável do conteúdo efetivamente publicado em uma versão. */
public record VersaoCampanhaDetalheResponse(
        Long campanhaId, Integer numero, Integer versaoAnterior, String titulo, String textoOriginal,
        LocalDate dataInicio, LocalDate dataFim, String responsavel, OffsetDateTime publicadoEm,
        UUID simulacaoAceitaId, UUID protocoloSimulacao, List<RegraHistoricaDTO> regras
) {
    public record RegraHistoricaDTO(
            Long id, String identificadorLogico, String blocoId, String trechoOrigem, String nome,
            String canal, Integer codMarca, String descrMarca, Integer codLoja, Integer codCargo,
            String descriCargo, String matricula, java.math.BigDecimal valorMinimo, Boolean minInclusivo,
            java.math.BigDecimal valorMaximo, Boolean maxInclusivo,
            com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase tipoOperacao,
            java.math.BigDecimal valorAjuste, String tipoBaseReferencia, java.math.BigDecimal taxaBaseConsultada,
            String descricaoReferencia, java.math.BigDecimal taxa, String explicacao, String pythonEquivalente,
            String pendencias, Boolean completa, java.math.BigDecimal bonusPercentual, java.math.BigDecimal bonusValorFixo,
            RastreabilidadeRegraDTO rastreabilidade
    ) {}
}
