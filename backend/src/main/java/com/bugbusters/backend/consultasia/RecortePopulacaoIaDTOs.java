package com.bugbusters.backend.consultasia;

import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Contratos de DTO para o recorte completo, reproduzível e versionado
 * da população selecionada fornecida ao serviço de IA (BACK-10 / IA-3 a IA-6).
 */
public final class RecortePopulacaoIaDTOs {

    private RecortePopulacaoIaDTOs() {}

    @Schema(description = "Critérios para montagem do recorte completo da população selecionada para IA")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecortePopulacaoIaRequest(
            @Schema(description = "ID da campanha cujas regras serão recortadas (opcional se 'regras' for fornecido)", example = "1")
            Long campanhaId,

            @Schema(description = "Número da versão específica da campanha (opcional; se omitido, usa a versão para revisão)", example = "1")
            Integer versaoCampanhaNumero,

            @Schema(description = "Se true, força a versão vigente publicada da campanha em vez da versão proposta", example = "false")
            Boolean usarVersaoVigente,

            @Schema(description = "Competência de referência no formato YYYY-MM para simulações com período histórico", example = "2026-09")
            String competencia,

            @Schema(description = "Data inicial explícita para o período de apuração/projeção", example = "2026-09-01")
            LocalDate dataInicio,

            @Schema(description = "Data final explícita para o período de apuração/projeção", example = "2026-09-30")
            LocalDate dataFim,

            @Schema(description = "Coleção de regras candidatas ou rascunho em elaboração (opcional se campanhaId for fornecido)")
            List<RegraItemRequest> regras,

            @Schema(description = "Hash canônico SHA-256 do recorte previamente recebido; se informado e houver divergência, retorna 409 Conflict", example = "a1b2c3d4...")
            String hashRecorteEsperado,

            @Schema(description = "Número da versão esperada da campanha; se divergente, lança 409 Conflict", example = "1")
            Integer versaoCampanhaEsperada
    ) {
        public RecortePopulacaoIaRequest(Long campanhaId) {
            this(campanhaId, null, null, null, null, null, null, null, null);
        }

        public RecortePopulacaoIaRequest(String competencia) {
            this(null, null, null, competencia, null, null, null, null, null);
        }
    }

    @Schema(description = "Definição do período temporal do recorte e fatores de projeção")
    public record PeriodoRecorteIaDTO(
            String competenciaReferencia,
            List<String> competenciasAbrangidas,
            LocalDate dataInicioCampanha,
            LocalDate dataFimCampanha,
            LocalDate dataInicioRecorte,
            LocalDate dataFimRecorte,
            long diasInclusivosCampanha,
            BigDecimal fatorProjecaoTemporal
    ) {
        public LocalDate dataInicio() { return dataInicioRecorte != null ? dataInicioRecorte : dataInicioCampanha; }
        public LocalDate dataFim() { return dataFimRecorte != null ? dataFimRecorte : dataFimCampanha; }
    }

    @Schema(description = "Metadados de versão e integridade da campanha")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FonteCampanhaRecorteIaDTO(
            Long campanhaId,
            String titulo,
            String estado,
            Long versaoId,
            Integer versaoNumero,
            String statusVersao,
            Long versaoControle,
            String statusAprovacaoCodigo,
            String hashArtefatosCodigo,
            BigDecimal orcamento,
            Boolean orcamentoConfirmado,
            String origemOrcamento
    ) {}

    @Schema(description = "Resumo estatístico de versões e lotes dos registros de vendas ou RH")
    public record ResumoVersaoRegistrosDTO(
            int totalRegistros,
            int versaoMinima,
            int versaoMaxima,
            int totalRegistrosEditados,
            List<String> lotesOrigemDistintos,
            List<String> lotesAlteracaoDistintos
    ) {}

    @Schema(description = "Metadados do lote de importação/preparação que deu origem ou alterou os dados")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoteFonteRecorteIaDTO(
            String idLote,
            Long reportId,
            String tipoBase,
            String competencia,
            String nomeArquivo,
            String hashConteudo,
            String origemLote,
            Long envioAnteriorId,
            OffsetDateTime finalizadoEm,
            boolean anexadoACampanha
    ) {}

    @Schema(description = "Versões e hashes canônicos das fontes de dados para garantir reproduzibilidade")
    public record VersoesFontesRecorteIaDTO(
            String hashRecorte,
            String hashVendas,
            String hashRh,
            String hashTaxasContratuais,
            String hashRegras,
            String hashPendenciasAceitas,
            FonteCampanhaRecorteIaDTO campanha,
            ResumoVersaoRegistrosDTO resumoVendas,
            ResumoVersaoRegistrosDTO resumoRh,
            List<LoteFonteRecorteIaDTO> lotesFontes
    ) {}

    @Schema(description = "Contagens e somatórios totais consolidados do recorte")
    public record TotaisRecorteIaDTO(
            int totalVendasPeriodoBase,
            BigDecimal valorTotalPeriodoBase,
            int totalVendasSelecionadas,
            BigDecimal valorTotalVendasSelecionadas,
            int totalColaboradores,
            int totalTaxasContratuais,
            int totalRegrasCampanha,
            int totalRegrasConcorrentesAtivas,
            int totalPendenciasAceitas,
            int totalLotesFontes
    ) {
        @JsonProperty("totalVendas")
        public int totalVendas() { return totalVendasSelecionadas; }

        @JsonProperty("totalColaboradoresRh")
        public int totalColaboradoresRh() { return totalColaboradores; }

        @JsonProperty("totalRegras")
        public int totalRegras() { return totalRegrasCampanha; }
    }

    @Schema(description = "Regra da campanha com filtros, faixas, bônus e artefatos de código")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RegraRecorteIaDTO(
            Long regraId,
            String blocoId,
            String nomeRegra,
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
            BigDecimal taxaBaseConsultada,
            BigDecimal taxa,
            BigDecimal bonusPercentual,
            BigDecimal bonusValorFixo,
            int especificidade,
            LocalDate dataInicio,
            LocalDate dataFim,
            String pythonEquivalente,
            String explicacao
    ) {
        public String nome() { return nomeRegra; }
        public String codigoPython() { return pythonEquivalente; }
    }

    @Schema(description = "Regra ativa de outra campanha que sobrepõe público e vigência com a população")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RegraConcorrenteRecorteIaDTO(
            Long regraId,
            Long campanhaId,
            String nomeCampanha,
            String nomeRegra,
            String canal,
            Integer codMarca,
            Integer codLoja,
            Integer codCargo,
            String matricula,
            BigDecimal taxa,
            int especificidade,
            LocalDate dataInicio,
            LocalDate dataFim,
            BigDecimal valorMinimo,
            BigDecimal valorMaximo
    ) {}

    @Schema(description = "Dados detalhados da transação de venda pertencente à população selecionada")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VendaRecorteIaDTO(
            UUID id,
            String matricula,
            Integer codLoja,
            String descrLoja,
            Integer codMarca,
            String descrMarca,
            Integer codCargo,
            String descriCargo,
            BigDecimal valorVenda,
            LocalDate dataVenda,
            String competencia,
            String canal,
            String tipoVenda,
            String idLoteOrigem,
            int versao,
            String origemUltimaAlteracao,
            String idLoteUltimaAlteracao,
            OffsetDateTime atualizadoEm
    ) {}

    @Schema(description = "Dados cadastrais e vínculos de RH do colaborador responsável pela venda")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ColaboradorRhRecorteIaDTO(
            UUID id,
            String matricula,
            Integer codLoja,
            String descrLoja,
            Integer codCargo,
            String descriCargo,
            LocalDate dataAdmissao,
            LocalDate dataDemissao,
            String idLoteOrigem,
            int versao,
            String origemUltimaAlteracao,
            String idLoteUltimaAlteracao,
            OffsetDateTime atualizadoEm
    ) {}

    @Schema(description = "Taxa contratual padrão em tb_basecomiss para fallback da população")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TaxaContratualRecorteIaDTO(
            UUID id,
            Integer codMarca,
            String descrMarca,
            Integer codCargo,
            String descriCargo,
            String competencia,
            LocalDate mesReferencia,
            BigDecimal taxa,
            String idLoteOrigem
    ) {
        @JsonProperty("percentual")
        public BigDecimal percentual() { return taxa; }
    }

    @Schema(description = "Pendência de lote com aceite formal para a campanha")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PendenciaAceitaRecorteIaDTO(
            Long inconsistenciaId,
            Long reportId,
            String idLote,
            String tipoBase,
            String competencia,
            Integer linha,
            String campo,
            String motivo,
            String severidade,
            String aceitaPor,
            OffsetDateTime aceitaEm,
            String motivoAceite
    ) {}

    @Schema(description = "Contrato completo do recorte reproduzível da população selecionada para IA")
    public record RecortePopulacaoIaResponse(
            UUID recorteId,
            OffsetDateTime geradoEm,
            PeriodoRecorteIaDTO periodo,
            VersoesFontesRecorteIaDTO versoesFontes,
            TotaisRecorteIaDTO totais,
            List<RegraRecorteIaDTO> regras,
            List<RegraConcorrenteRecorteIaDTO> regrasConcorrentesAtivas,
            List<VendaRecorteIaDTO> vendas,
            List<ColaboradorRhRecorteIaDTO> dadosRh,
            List<TaxaContratualRecorteIaDTO> taxasContratuais,
            List<PendenciaAceitaRecorteIaDTO> pendenciasAceitas
    ) {
        @JsonProperty("campanhaId")
        public Long campanhaId() {
            return versoesFontes != null && versoesFontes.campanha() != null ? versoesFontes.campanha().campanhaId() : null;
        }

        @JsonProperty("nomeCampanha")
        public String nomeCampanha() {
            return versoesFontes != null && versoesFontes.campanha() != null ? versoesFontes.campanha().titulo() : null;
        }

        @JsonProperty("hashRecorte")
        public String hashRecorte() {
            return versoesFontes != null ? versoesFontes.hashRecorte() : null;
        }

        @JsonProperty("hashVendas")
        public String hashVendas() {
            return versoesFontes != null ? versoesFontes.hashVendas() : null;
        }

        @JsonProperty("hashRh")
        public String hashRh() {
            return versoesFontes != null ? versoesFontes.hashRh() : null;
        }

        @JsonProperty("hashTaxasContratuais")
        public String hashTaxasContratuais() {
            return versoesFontes != null ? versoesFontes.hashTaxasContratuais() : null;
        }

        @JsonProperty("hashRegras")
        public String hashRegras() {
            return versoesFontes != null ? versoesFontes.hashRegras() : null;
        }

        @JsonProperty("hashPendenciasAceitas")
        public String hashPendenciasAceitas() {
            return versoesFontes != null ? versoesFontes.hashPendenciasAceitas() : null;
        }

        @JsonProperty("colaboradoresRh")
        public List<ColaboradorRhRecorteIaDTO> colaboradoresRh() {
            return dadosRh;
        }
    }

    @Schema(description = "Requisição para conferência de consistência das fontes do recorte")
    public record VerificarConsistenciaRecorteIaRequest(
            Long campanhaId,
            String competencia,
            String hashRecorteEsperado,
            String hashVendasEsperado,
            String hashRhEsperado,
            String hashTaxasEsperado,
            String hashRegrasEsperado,
            String hashPendenciasEsperado
    ) {
        public VerificarConsistenciaRecorteIaRequest(Long campanhaId, String competencia, String hashRecorteEsperado) {
            this(campanhaId, competencia, hashRecorteEsperado, null, null, null, null, null);
        }
    }

    @Schema(description = "Resultado da verificação de consistência e mutação de dados")
    public record ConsistenciaRecorteIaResponse(
            boolean consistente,
            String hashRecorteAtual,
            String hashVendasAtual,
            String hashRhAtual,
            String hashTaxasAtual,
            String hashRegrasAtual,
            String hashPendenciasAtual,
            List<String> fontesAlteradas,
            String mensagem
    ) {}
}
