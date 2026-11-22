package com.bugbusters.backend.model;

/**
 * Origem do valor monetário de orçamento de uma campanha/proposta (BACK-8).
 */
public enum OrigemOrcamentoCampanha {
    NAO_DEFINIDO,
    EXTRAIDO_IA,
    CONFIRMADO_USUARIO,
    ALTERADO_USUARIO,
    INFORMADO_USUARIO
}
