package com.bugbusters.backend.service;

import java.math.BigDecimal;

public record ResolucaoTaxaResult(
    boolean sucesso,
    BigDecimal taxa,
    Long idRegra,
    String origemTaxa,
    String motivoImpedimento
) {
    public static ResolucaoTaxaResult sucesso(BigDecimal taxa, Long idRegra, String origemTaxa) {
        return new ResolucaoTaxaResult(true, taxa, idRegra, origemTaxa, null);
    }

    public static ResolucaoTaxaResult impedido(String motivo) {
        return new ResolucaoTaxaResult(false, null, null, null, motivo);
    }
}
