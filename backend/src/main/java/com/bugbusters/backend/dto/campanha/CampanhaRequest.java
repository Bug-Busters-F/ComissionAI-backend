package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.Marca;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Dados para cadastro ou edição de campanha com regra vinculada")
public record CampanhaRequest(
        @Schema(description = "Título descritivo da campanha", example = "Campanha Black Friday 2026")
        @NotBlank(message = "O título da campanha é obrigatório")
        String titulo,

        @Schema(description = "Texto original em linguagem natural que originou a proposta", example = "Comissão de 5% para vendas no e-commerce em dezembro")
        @NotBlank(message = "O texto original da campanha é obrigatório")
        String textoOriginal,

        @Schema(description = "Canal de aplicação da regra (dimensão própria, opcional)", example = "ECOMMERCE")
        String canal,

        @Schema(description = "Código numérico da marca", example = "10")
        Integer codMarca,

        @Schema(description = "Descrição textual / cor da empresa confidencial (ex: Vermelho, PRETO)", example = "VERMELHO")
        String descrMarca,

        @Schema(description = "Código numérico da loja", example = "75")
        Integer codLoja,

        @Schema(description = "Código numérico do cargo", example = "100")
        Integer codCargo,

        @Schema(description = "Descrição textual do cargo", example = "VENDEDOR LOJA")
        String descriCargo,

        @Schema(description = "Matrícula individual do vendedor", example = "MATRIC-56")
        String matricula,

        @Schema(description = "Taxa decimal de comissão (ex: 0.0500 = 5%). Obrigatória no contrato de uma regra; quando `regras` é informada, a taxa de cada regra vale e este campo é ignorado.", example = "0.0500")
        @Positive(message = "A taxa de comissão deve ser maior que zero")
        BigDecimal taxa,

        @Schema(description = "Data de início da vigência. Se nula, assume a data atual.", example = "2026-10-01")
        LocalDate dataInicio,

        @Schema(description = "Data final da vigência. Se ausente, aplica-se automaticamente 30 dias a partir da data atual.", example = "2026-10-31")
        LocalDate dataFim,

        @Schema(description = "Estado da campanha (opcional na criação/edição)", example = "ATIVA")
        EstadoCampanha estado,

        @Schema(description = "Lista opcional de regras da campanha. Quando informada e não vazia, substitui o contrato de uma regra (campos de público/taxa deste nível são ignorados). Todas as regras herdam a vigência da campanha.")
        @Valid
        List<RegraRequest> regras
) {
    @Schema(description = "Regra individual de uma campanha com múltiplas regras")
    public record RegraRequest(
            @Schema(description = "Nome da regra (opcional; padrão: 'Regra N - título da campanha')", example = "Vendedores loja 75")
            String nome,
            @Schema(description = "Canal de aplicação (opcional)", example = "ECOMMERCE")
            String canal,
            @Schema(description = "Código numérico da marca", example = "10")
            Integer codMarca,
            @Schema(description = "Descrição textual da marca", example = "PRETO")
            String descrMarca,
            @Schema(description = "Código numérico da loja", example = "75")
            Integer codLoja,
            @Schema(description = "Código numérico do cargo", example = "100")
            Integer codCargo,
            @Schema(description = "Descrição textual do cargo", example = "VENDEDOR LOJA")
            String descriCargo,
            @Schema(description = "Matrícula individual", example = "MATRIC-56")
            String matricula,
            @Schema(description = "Taxa decimal de comissão", example = "0.0300")
            @NotNull(message = "A taxa de comissão da regra é obrigatória")
            @Positive(message = "A taxa de comissão deve ser maior que zero")
            BigDecimal taxa,
            @Schema(description = "Valor mínimo da venda para a regra valer (faixa por venda; opcional)", example = "0.00")
            @PositiveOrZero(message = "O valor mínimo da faixa não pode ser negativo")
            BigDecimal valorMin,
            @Schema(description = "Valor máximo da venda para a regra valer (faixa por venda; opcional)", example = "10000.00")
            @Positive(message = "O valor máximo da faixa deve ser maior que zero")
            BigDecimal valorMax,
            @Schema(description = "Se o valor mínimo é inclusivo (padrão true)", example = "true")
            Boolean minInclusivo,
            @Schema(description = "Se o valor máximo é inclusivo (padrão true)", example = "false")
            Boolean maxInclusivo,
            @Schema(description = "Bônus percentual adicional sobre o valor da venda (0 < x <= 1)", example = "0.0100")
            @Positive(message = "O bônus percentual deve ser maior que zero")
            @DecimalMax(value = "1.0000", message = "O bônus percentual não pode ultrapassar 1.0000 (100%)")
            BigDecimal bonusPercentual,
            @Schema(description = "Bônus em valor fixo (R$) adicionado por venda elegível", example = "50.00")
            @Positive(message = "O bônus de valor fixo deve ser maior que zero")
            BigDecimal bonusValorFixo
    ) {
        public RegraRequest {
            if (descrMarca != null && !descrMarca.isBlank()) {
                descrMarca = Marca.padronizar(descrMarca);
                if (codMarca == null) {
                    codMarca = Marca.buscarPorNome(descrMarca).map(Marca::getCodigo).orElse(null);
                }
            } else if (codMarca != null) {
                descrMarca = Marca.buscarPorCodigo(codMarca).map(Marca::getDescricao).orElse(null);
            }
        }
    }

    /** Valida a taxa do contrato de uma regra: obrigatória apenas quando `regras` não foi informada. */
    @JsonIgnore
    @AssertTrue(message = "A taxa de comissão é obrigatória")
    public boolean isTaxaOuRegrasInformada() {
        return taxa != null || possuiRegras();
    }

    /** Indica se o pedido usa o contrato com lista de regras. */
    public boolean possuiRegras() {
        return regras != null && !regras.isEmpty();
    }

    public CampanhaRequest {
        if (descrMarca != null && !descrMarca.isBlank()) {
            descrMarca = Marca.padronizar(descrMarca);
            if (codMarca == null) {
                codMarca = Marca.buscarPorNome(descrMarca).map(Marca::getCodigo).orElse(null);
            }
        } else if (codMarca != null) {
            descrMarca = Marca.buscarPorCodigo(codMarca).map(Marca::getDescricao).orElse(null);
        }
    }

    public CampanhaRequest(String titulo, String textoOriginal, String canal, Integer codMarca, String descrMarca,
                           Integer codLoja, Integer codCargo, String descriCargo, String matricula,
                           BigDecimal taxa, LocalDate dataInicio, LocalDate dataFim) {
        this(titulo, textoOriginal, canal, codMarca, descrMarca, codLoja, codCargo, descriCargo, matricula, taxa, dataInicio, dataFim, null, null);
    }

    public CampanhaRequest(String titulo, String textoOriginal, String canal, BigDecimal taxa, LocalDate dataInicio, LocalDate dataFim) {
        this(titulo, textoOriginal, canal, null, null, null, null, null, null, taxa, dataInicio, dataFim, null, null);
    }

    public CampanhaRequest(String titulo, String textoOriginal, String canal, Integer codMarca,
                           Integer codLoja, Integer codCargo, String descriCargo, String matricula,
                           BigDecimal taxa, LocalDate dataInicio, LocalDate dataFim) {
        this(titulo, textoOriginal, canal, codMarca, null, codLoja, codCargo, descriCargo, matricula, taxa, dataInicio, dataFim, null, null);
    }

    public CampanhaRequest(String titulo, String textoOriginal, String canal, Integer codMarca, String descrMarca,
                           Integer codLoja, Integer codCargo, String descriCargo, String matricula,
                           BigDecimal taxa, LocalDate dataInicio, LocalDate dataFim, EstadoCampanha estado) {
        this(titulo, textoOriginal, canal, codMarca, descrMarca, codLoja, codCargo, descriCargo, matricula, taxa, dataInicio, dataFim, estado, null);
    }
}
