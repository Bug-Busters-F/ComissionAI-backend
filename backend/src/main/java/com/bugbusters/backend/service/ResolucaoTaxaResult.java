package com.bugbusters.backend.service;

import java.math.BigDecimal;

/**
 * Resultado da resolução de taxa de comissão e validação de vínculos (BUG-23).
 * Inclui os metadados de versão/referência e snapshot de parâmetros no momento do cálculo,
 * além do bônus da regra vencedora (percentual e/ou valor fixo sobre a venda), quando houver.
 */
public record ResolucaoTaxaResult(
    boolean sucesso,
    BigDecimal taxa,
    Long idRegra,
    String origemTaxa,
    String motivoImpedimento,
    String versaoOuReferenciaRegra,
    String parametrosSnapshot,
    BigDecimal bonusPercentual,
    BigDecimal bonusValorFixo,
    Integer versaoCampanhaNumero
) {
    public ResolucaoTaxaResult(boolean sucesso, BigDecimal taxa, Long idRegra, String origemTaxa, String motivoImpedimento,
                               String versaoOuReferenciaRegra, String parametrosSnapshot, BigDecimal bonusPercentual, BigDecimal bonusValorFixo) {
        this(sucesso, taxa, idRegra, origemTaxa, motivoImpedimento, versaoOuReferenciaRegra, parametrosSnapshot, bonusPercentual, bonusValorFixo, null);
    }

    public ResolucaoTaxaResult(boolean sucesso, BigDecimal taxa, Long idRegra, String origemTaxa, String motivoImpedimento,
                               String versaoOuReferenciaRegra, String parametrosSnapshot) {
        this(sucesso, taxa, idRegra, origemTaxa, motivoImpedimento, versaoOuReferenciaRegra, parametrosSnapshot, null, null, null);
    }

    public ResolucaoTaxaResult(boolean sucesso, BigDecimal taxa, Long idRegra, String origemTaxa, String motivoImpedimento) {
        this(sucesso, taxa, idRegra, origemTaxa, motivoImpedimento,
                idRegra != null ? "REGRA#" + idRegra : (origemTaxa != null ? origemTaxa : "PADRAO"),
                null, null, null, null);
    }

    public static ResolucaoTaxaResult sucesso(BigDecimal taxa, Long idRegra, String origemTaxa) {
        return new ResolucaoTaxaResult(true, taxa, idRegra, origemTaxa, null);
    }

    public static ResolucaoTaxaResult sucesso(BigDecimal taxa, Long idRegra, String origemTaxa, String versaoOuReferenciaRegra, String parametrosSnapshot) {
        return new ResolucaoTaxaResult(true, taxa, idRegra, origemTaxa, null, versaoOuReferenciaRegra, parametrosSnapshot);
    }

    public static ResolucaoTaxaResult sucesso(BigDecimal taxa, Long idRegra, String origemTaxa, String versaoOuReferenciaRegra,
                                              String parametrosSnapshot, BigDecimal bonusPercentual, BigDecimal bonusValorFixo) {
        return new ResolucaoTaxaResult(true, taxa, idRegra, origemTaxa, null, versaoOuReferenciaRegra, parametrosSnapshot,
                bonusPercentual, bonusValorFixo, null);
    }

    public static ResolucaoTaxaResult sucesso(BigDecimal taxa, Long idRegra, String origemTaxa, String versaoOuReferenciaRegra,
                                              String parametrosSnapshot, BigDecimal bonusPercentual, BigDecimal bonusValorFixo,
                                              Integer versaoCampanhaNumero) {
        return new ResolucaoTaxaResult(true, taxa, idRegra, origemTaxa, null, versaoOuReferenciaRegra, parametrosSnapshot,
                bonusPercentual, bonusValorFixo, versaoCampanhaNumero);
    }

    public static ResolucaoTaxaResult impedido(String motivo) {
        return new ResolucaoTaxaResult(false, null, null, null, motivo, null, null, null, null, null);
    }
}
