package com.bugbusters.backend.model;

/**
 * Estados do ciclo de vida da esteira de aprovação de uma campanha (BUG-99 / BACK-4).
 * A esteira avança da elaboração (Etapa 1) até a aprovação gerencial (Etapa 4),
 * podendo ser devolvida a qualquer momento com justificativa obrigatória.
 */
public enum EstadoEsteiraCampanha {
    EM_ELABORACAO,
    EM_REVISAO_TECNICA,
    EM_SIMULACAO,
    EM_APROVACAO_GERENCIAL,
    DEVOLVIDA,
    APROVADA
}

