package com.bugbusters.backend.model;

/**
 * Tipos de ação e movimentação auditável na esteira da campanha (BUG-99 / BACK-4).
 */
public enum TipoAcaoEsteira {
    AVANCO_ETAPA,
    RETORNO_ETAPA,
    APROVACAO_TECNICA,
    REJEICAO_TECNICA,
    APROVACAO_GERENCIAL,
    DEVOLUCAO_GERENCIAL,
    EDICAO_REGRAS
}

