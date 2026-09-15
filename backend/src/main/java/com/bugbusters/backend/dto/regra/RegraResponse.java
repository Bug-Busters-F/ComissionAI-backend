package com.bugbusters.backend.dto.regra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados detalhados da regra cadastrada")
public record RegraResponse(
    @Schema(description = "Identificador único da regra", example = "1")
    Long id,

    @Schema(description = "Nome da regra", example = "Comissão Black Friday E-commerce")
    String nome,

    @Schema(description = "Canal de venda vinculado", example = "ECOMMERCE")
    String canal,

    @Schema(description = "Código da marca vinculada", example = "10")
    Integer codMarca,

    @Schema(description = "Descrição textual / cor da empresa confidencial", example = "VERMELHO")
    String descrMarca,

    @Schema(description = "Código da loja vinculada", example = "75")
    Integer codLoja,

    @Schema(description = "Código do cargo vinculado", example = "100")
    Integer codCargo,

    @Schema(description = "Descrição textual do cargo", example = "VENDEDOR LOJA")
    String descriCargo,

    @Schema(description = "Matrícula do vendedor vinculado", example = "MATRIC-56")
    String matricula,

    @Schema(description = "Taxa decimal aplicada", example = "0.0500")
    BigDecimal taxa,

    @Schema(description = "Início da vigência", example = "2026-10-01")
    LocalDate dataInicio,

    @Schema(description = "Fim da vigência", example = "2026-10-31")
    LocalDate dataFim,

    @Schema(description = "Estado atual da regra", example = "ATIVA")
    StatusRegra status,

    @Schema(description = "Data e hora de cadastro", example = "2026-09-08T19:00:00Z")
    OffsetDateTime criadoEm
) {
    public RegraResponse(Long id, String nome, String canal, BigDecimal taxa, LocalDate dataInicio, LocalDate dataFim, StatusRegra status, OffsetDateTime criadoEm) {
        this(id, nome, canal, null, null, null, null, null, null, taxa, dataInicio, dataFim, status, criadoEm);
    }

    public RegraResponse(Long id, String nome, String canal, Integer codMarca, Integer codLoja,
                         Integer codCargo, String descriCargo, String matricula,
                         BigDecimal taxa, LocalDate dataInicio, LocalDate dataFim,
                         StatusRegra status, OffsetDateTime criadoEm) {
        this(id, nome, canal, codMarca, null, codLoja, codCargo, descriCargo, matricula, taxa, dataInicio, dataFim, status, criadoEm);
    }
}
