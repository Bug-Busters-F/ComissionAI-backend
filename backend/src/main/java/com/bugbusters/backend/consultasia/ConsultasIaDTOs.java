package com.bugbusters.backend.consultasia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Contratos das consultas somente leitura consumidas pelas ferramentas do serviço de IA.
 * Convenção: consulta válida sem resultado devolve HTTP 200 com {@code encontrado=false};
 * HTTP 4xx/5xx significa erro real (parâmetro inválido ou falha), no formato ApiErrorResponse.
 */
public final class ConsultasIaDTOs {

    private ConsultasIaDTOs() {}

    @Schema(description = "Item de catálogo (marca, loja ou cargo)")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemCatalogoDTO(
            @Schema(description = "Código numérico", example = "10") Integer codigo,
            @Schema(description = "Descrição cadastrada", example = "PRETO") String descricao,
            @Schema(description = "Código da marca da loja (null quando a base não relaciona loja e marca)") Integer codMarca
    ) {}

    @Schema(description = "Resultado de consulta a catálogo")
    public record CatalogoResponse(
            @Schema(description = "false quando nenhum item atende ao filtro") boolean encontrado,
            @Schema(description = "Itens que atendem ao filtro; mais de um indica ambiguidade") List<ItemCatalogoDTO> itens
    ) {}

    @Schema(description = "Vínculo de uma matrícula")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MatriculaResponse(
            boolean encontrado,
            String matricula,
            Integer codLoja,
            Integer codCargo,
            String descriCargo,
            Integer codMarca,
            LocalDate dataAdmissao,
            LocalDate dataDemissao
    ) {
        public static MatriculaResponse naoEncontrada() {
            return new MatriculaResponse(false, null, null, null, null, null, null, null);
        }
    }

    @Schema(description = "Taxa base de comissão por marca, cargo e competência")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BaseComissaoResponse(
            boolean encontrado,
            Integer codMarca,
            Integer codCargo,
            @Schema(description = "Competência solicitada (YYYY-MM)", example = "2026-09") String competencia,
            @Schema(description = "Taxa decimal com escala 4", example = "0.0250") BigDecimal taxa,
            @Schema(description = "Mês efetivamente encontrado (pode ser o mais recente disponível)", example = "2026-08") String competenciaUsada
    ) {}

    @Schema(description = "Condições de público da regra proposta")
    public record CondicoesDTO(
            String canal,
            Integer codMarca,
            Integer codLoja,
            Integer codCargo,
            String matricula
    ) {}

    @Schema(description = "Regra proposta cuja concorrência com as regras ativas será consultada")
    public record RegrasConcorrentesRequest(
            @Schema(description = "Condições de público; campos nulos significam 'qualquer'") CondicoesDTO condicoes,
            @Schema(description = "Início da vigência (obrigatório)", example = "2026-10-01") LocalDate dataInicio,
            @Schema(description = "Fim da vigência (null = sem fim definido)", example = "2026-10-31") LocalDate dataFim,
            @Schema(description = "Campanha cujas regras devem ser ignoradas (edição: a proposta substitui a versão anterior)", example = "12") String ignorarCampanhaId,
            @Schema(description = "Opcional: valor mínimo da faixa por venda da proposta") BigDecimal valorMin,
            @Schema(description = "Opcional: valor máximo da faixa por venda da proposta") BigDecimal valorMax,
            @Schema(description = "Opcional: mínimo inclusivo (padrão true)") Boolean minInclusivo,
            @Schema(description = "Opcional: máximo inclusivo (padrão true)") Boolean maxInclusivo
    ) {}

    @Schema(description = "Regra ativa que pode concorrer com a proposta")
    public record ConflitoDTO(
            Long idRegra,
            String campanhaId,
            BigDecimal taxa,
            @Schema(description = "Peso de especificidade (matrícula 16, loja 8, cargo 4, canal 2, marca 1)") int especificidade,
            LocalDate dataInicio,
            LocalDate dataFim
    ) {}

    public record RegrasConcorrentesResponse(List<ConflitoDTO> conflitos) {}

    @Schema(description = "Venda disponibilizada para simulação")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record VendaSimulacaoDTO(
            String id,
            String matricula,
            Integer codLoja,
            Integer codMarca,
            Integer codCargo,
            BigDecimal valorVenda,
            LocalDate dataVenda,
            String canal
    ) {}

    @Schema(description = "Página de resultados")
    public record PaginaDTO<T>(
            @Schema(description = "false quando não há dados ou a cobertura de base da competência é insuficiente")
            boolean encontrado,
            List<T> content,
            int number,
            int size,
            int totalPages,
            long totalElements,
            boolean last
    ) {
        public PaginaDTO(List<T> content, int number, int size, int totalPages, long totalElements, boolean last) {
            this(content != null && !content.isEmpty(), content, number, size, totalPages, totalElements, last);
        }

        public static <T> PaginaDTO<T> coberturaInsuficiente(int number, int size) {
            return new PaginaDTO<>(false, List.of(), number, size, 0, 0L, true);
        }
    }
}
