package com.bugbusters.backend.service.lock;

public interface TravaCompetencia {
    /**
     * Adquire a trava pessimista (SELECT ... FOR UPDATE) para a competência informada.
     * Deve ser chamada dentro de uma transação ativa.
     *
     * @param competencia Competência no formato YYYY-MM
     */
    void adquirirTrava(String competencia);
}
