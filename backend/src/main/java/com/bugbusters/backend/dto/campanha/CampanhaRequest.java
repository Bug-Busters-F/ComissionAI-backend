package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.model.Marca;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

        @Schema(description = "Taxa decimal de comissão (ex: 0.0500 = 5%)", example = "0.0500")
        @NotNull(message = "A taxa de comissão é obrigatória")
        @Positive(message = "A taxa de comissão deve ser maior que zero")
        BigDecimal taxa,

        @Schema(description = "Data de início da vigência. Se nula, assume a data atual.", example = "2026-10-01")
        LocalDate dataInicio,

        @Schema(description = "Data final da vigência. Se ausente, aplica-se automaticamente 30 dias a partir da data atual.", example = "2026-10-31")
        LocalDate dataFim
) {
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

    public CampanhaRequest(String titulo, String textoOriginal, String canal, BigDecimal taxa, LocalDate dataInicio, LocalDate dataFim) {
        this(titulo, textoOriginal, canal, null, null, null, null, null, null, taxa, dataInicio, dataFim);
    }

    public CampanhaRequest(String titulo, String textoOriginal, String canal, Integer codMarca,
                           Integer codLoja, Integer codCargo, String descriCargo, String matricula,
                           BigDecimal taxa, LocalDate dataInicio, LocalDate dataFim) {
        this(titulo, textoOriginal, canal, codMarca, null, codLoja, codCargo, descriCargo, matricula, taxa, dataInicio, dataFim);
    }
}