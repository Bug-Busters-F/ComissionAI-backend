package com.bugbusters.backend.dto.regra;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bugbusters.backend.model.Marca;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Payload para criação ou atualização manual de regra de comissão")
public record RegraRequest(
    @Schema(description = "Nome de identificação da regra", example = "Comissão Black Friday E-commerce")
    @NotBlank(message = "O nome da regra é obrigatório")
    String nome,

    @Schema(description = "Canal de venda (dimensão independente de loja/marca, opcional)", example = "ECOMMERCE")
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

    @Schema(description = "Matrícula do vendedor para regra individual", example = "MATRIC-56")
    String matricula,

    @Schema(description = "Taxa de comissão em formato decimal (0.05 = 5%)", example = "0.0500")
    @NotNull(message = "A taxa de comissão é obrigatória")
    @Positive(message = "A taxa deve ser maior que zero")
    BigDecimal taxa,

    @Schema(description = "Data de início de vigência (YYYY-MM-DD)", example = "2026-10-01")
    @NotNull(message = "A data de início é obrigatória")
    LocalDate dataInicio,

    @Schema(description = "Data de fim da vigência. Se ausente, aplicam-se 30 dias automaticamente.", example = "2026-10-31")
    LocalDate dataFim
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

    public RegraRequest(String nome, String canal, BigDecimal taxa, LocalDate dataInicio, LocalDate dataFim) {
        this(nome, canal, null, null, null, null, null, null, taxa, dataInicio, dataFim);
    }

    public RegraRequest(String nome, String canal, Integer codMarca, Integer codLoja,
                        Integer codCargo, String descriCargo, String matricula,
                        BigDecimal taxa, LocalDate dataInicio, LocalDate dataFim) {
        this(nome, canal, codMarca, null, codLoja, codCargo, descriCargo, matricula, taxa, dataInicio, dataFim);
    }
}