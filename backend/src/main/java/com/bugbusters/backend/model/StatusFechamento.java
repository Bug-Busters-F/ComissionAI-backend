package com.bugbusters.backend.model;

/**
 * Status do ciclo de vida do Fechamento Final de Competência (BACK-11).
 */
public enum StatusFechamento {
    /**
     * O fechamento final foi iniciado e está em processamento concorrente sob trava.
     */
    PROCESSANDO,

    /**
     * O fechamento final foi concluído com sucesso, consolidado e lacrado contra mutações.
     */
    CONCLUIDO,

    /**
     * O processamento falhou e as travas foram liberadas sem registrar fechamento concluído.
     */
    FALHOU
}
