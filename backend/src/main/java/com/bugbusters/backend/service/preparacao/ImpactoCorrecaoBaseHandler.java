package com.bugbusters.backend.service.preparacao;

import com.bugbusters.backend.model.LotePreparacao;

import java.util.Collection;
import java.util.UUID;

/**
 * Ponto de extensão SPI para verificação e propagação de impactos de correções nas bases gerais (integração com BACK-6).
 */
public interface ImpactoCorrecaoBaseHandler {

    /**
     * Valida se as alterações de registros de RH podem ser efetivadas ou se geram impedimentos/conflitos.
     *
     * @param lotePreparacao Lote em efetivação
     * @param matriculasAlteradas Matrículas existentes que terão cargo, loja ou vigência atualizados
     */
    void validarImpactoRh(LotePreparacao lotePreparacao, Collection<String> matriculasAlteradas);

    /**
     * Valida se as alterações de registros de Vendas podem ser efetivadas ou se geram impedimentos/conflitos.
     *
     * @param lotePreparacao Lote em efetivação
     * @param idsVendasAlteradas IDs das vendas existentes que terão valores ou dados alterados
     */
    void validarImpactoVendas(LotePreparacao lotePreparacao, Collection<UUID> idsVendasAlteradas);
}
